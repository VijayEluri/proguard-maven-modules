/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2008 Eric Lafortune (eric@graphics.cornell.edu)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package proguard.optimize.info;

import proguard.classfile.*;
import proguard.classfile.util.*;
import proguard.classfile.visitor.MemberVisitor;

/**
 * This MemberVisitor counts the parameters and marks the used parameters
 * of the methods that it visits. It also marks the 'this' parameters of
 * methods that have hierarchies.
 *
 * @author Eric Lafortune
 */
public class ParameterUsageMarker
extends      SimplifiedVisitor
implements   MemberVisitor
{
    private static final boolean DEBUG = false;


    private final VariableUsageMarker variableUsageMarker = new VariableUsageMarker();


    // Implementations for MemberVisitor.

    public void visitProgramMethod(ProgramClass programClass, ProgramMethod programMethod)
    {
        int parameterSize =
            ClassUtil.internalMethodParameterSize(programMethod.getDescriptor(programClass),
                                                  programMethod.getAccessFlags());

        if (parameterSize > 0)
        {
            // Is it a native method?
            int accessFlags = programMethod.getAccessFlags();
            if ((accessFlags & ClassConstants.INTERNAL_ACC_NATIVE) != 0)
            {
                // Mark all parameters.
                markUsedParameters(programMethod, -1L);
            }

            // Is it an abstract method?
            else if ((accessFlags & ClassConstants.INTERNAL_ACC_ABSTRACT) != 0)
            {
                // Mark the 'this' parameter.
                markParameterUsed(programMethod, 0);
            }

            // Is it a non-native, concrete method?
            else
            {
                // Is the method not static, but synchronized, or can it have
                // other implementations, or is it a class instance initializer?
                if ((accessFlags & ClassConstants.INTERNAL_ACC_STATIC) == 0 &&
                    ((accessFlags & ClassConstants.INTERNAL_ACC_SYNCHRONIZED) != 0 ||
                     programClass.mayHaveImplementations(programMethod)            ||
                     programMethod.getName(programClass).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT)))
                {
                    // Mark the 'this' parameter.
                    markParameterUsed(programMethod, 0);
                }

                // Figure out the local variables that are used by the code.
                programMethod.attributesAccept(programClass, variableUsageMarker);

                // Mark the parameters that are used by the code.
                for (int index = 0; index < parameterSize; index++)
                {
                    if (variableUsageMarker.isVariableUsed(index))
                    {
                        markParameterUsed(programMethod, index);
                    }
                }

                // Mark the category 2 parameters that are half-used.
                InternalTypeEnumeration internalTypeEnumeration =
                    new InternalTypeEnumeration(programMethod.getDescriptor(programClass));

                // All parameters of non-static methods are shifted by one in
                // the local variable frame.
                int index =
                    (accessFlags & ClassConstants.INTERNAL_ACC_STATIC) != 0 ?
                        0 : 1;

                while (internalTypeEnumeration.hasMoreTypes())
                {
                    String type = internalTypeEnumeration.nextType();
                    if (ClassUtil.isInternalCategory2Type(type))
                    {
                        if (variableUsageMarker.isVariableUsed(index) ||
                            variableUsageMarker.isVariableUsed(index+1))
                        {
                            markParameterUsed(programMethod, index);
                            markParameterUsed(programMethod, index+1);
                        }

                        index++;
                    }

                    index++;
                }
            }

            if (DEBUG)
            {
                System.out.print("ParameterUsageMarker: ["+programClass.getName() +"."+programMethod.getName(programClass)+programMethod.getDescriptor(programClass)+"]: ");
                for (int index = 0; index < parameterSize; index++)
                {
                    System.out.print(isParameterUsed(programMethod, index) ? '+' : '-');
                }
                System.out.println();
            }

        }

        // Set the parameter size.
        setParameterSize(programMethod, parameterSize);
    }


    public void visitLibraryMethod(LibraryClass libraryClass, LibraryMethod libraryMethod)
    {
        // Can the method have other implementations?
        if (libraryClass.mayHaveImplementations(libraryMethod))
        {
            // All implementations must keep all parameters of this method,
            // including the 'this' parameter.
            long usedParameters = -1L;

            // Mark it.
            markUsedParameters(libraryMethod, usedParameters);
        }
    }


    // Small utility methods.

    /**
     * Sets the total size of the parameters.
     */
    private static void setParameterSize(Method method, int parameterSize)
    {
        MethodOptimizationInfo info = MethodOptimizationInfo.getMethodOptimizationInfo(method);
        if (info != null)
        {
            info.setParameterSize(parameterSize);
        }
    }


    /**
     * Returns the total size of the parameters.
     */
    public static int getParameterSize(Method method)
    {
        MethodOptimizationInfo info = MethodOptimizationInfo.getMethodOptimizationInfo(method);
        return info != null ? info.getParameterSize() : 0;
    }


    /**
     * Marks the given parameter as being used.
     */
    public static void markParameterUsed(Method method, int variableIndex)
    {
        MethodOptimizationInfo info = MethodOptimizationInfo.getMethodOptimizationInfo(method);
        if (info != null)
        {
            info.setParameterUsed(variableIndex);
        }
    }


    /**
     * Marks the given parameters as being used.
     */
    public static void markUsedParameters(Method method, long usedParameters)
    {
        MethodOptimizationInfo info = MethodOptimizationInfo.getMethodOptimizationInfo(method);
        if (info != null)
        {
            info.setUsedParameters(info.getUsedParameters() | usedParameters);
        }
    }


    /**
     * Returns whether the given parameter is being used.
     */
    public static boolean isParameterUsed(Method method, int variableIndex)
    {
        MethodOptimizationInfo info = MethodOptimizationInfo.getMethodOptimizationInfo(method);
        return info == null ||
               info.isParameterUsed(variableIndex);
    }


    /**
     * Returns which parameters are being used.
     */
    public static long getUsedParameters(Method method)
    {
        MethodOptimizationInfo info = MethodOptimizationInfo.getMethodOptimizationInfo(method);
        return info != null ? info.getUsedParameters() : -1L;
    }


    /**
     * Returns a bit mask of 1-bits of the given size.
     */
    private int parameterMask(int parameterSize)
    {
        return (1 << parameterSize) - 1;
    }


    /**
     * Returns the parameter size of the given method, including the 'this'
     * parameter, if any.
     */
    private int parameterSize(Clazz clazz, Method method)
    {

        return ClassUtil.internalMethodParameterSize(method.getDescriptor(clazz),
                                                     method.getAccessFlags());
    }
}
