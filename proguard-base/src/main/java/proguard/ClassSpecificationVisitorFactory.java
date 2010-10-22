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
package proguard;

import proguard.classfile.attribute.annotation.visitor.*;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.visitor.*;

import java.util.List;

/**
 * This factory creates visitors to efficiently travel to specified classes and
 * class members.
 *
 * @author Eric Lafortune
 */
public class ClassSpecificationVisitorFactory
{
    /**
     * Constructs a ClassPoolVisitor to efficiently travel to the specified
     * classes and class members.
     *
     * @param keepSpecifications the list of KeepSpecification instances,
     *                           defining of the classes and class members to
     *                           visit.
     * @param classVisitor       the ClassVisitor to be applied to matching
     *                           classes.
     * @param memberVisitor      the MemberVisitor to be applied to matching
     *                           class members.
     */
    public static ClassPoolVisitor createClassPoolVisitor(List          keepSpecifications,
                                                          ClassVisitor  classVisitor,
                                                          MemberVisitor memberVisitor,
                                                          boolean       shrinking,
                                                          boolean       optimizing,
                                                          boolean       obfuscating)
    {
        MultiClassPoolVisitor multiClassPoolVisitor = new MultiClassPoolVisitor();

        if (keepSpecifications != null)
        {
            for (int index = 0; index < keepSpecifications.size(); index++)
            {
                KeepSpecification keepSpecification =
                    (KeepSpecification)keepSpecifications.get(index);

                if ((shrinking   && !keepSpecification.allowShrinking)    ||
                    (optimizing  && !keepSpecification.allowOptimization) ||
                    (obfuscating && !keepSpecification.allowObfuscation))
                {
                    multiClassPoolVisitor.addClassPoolVisitor(
                        createClassPoolVisitor(keepSpecification,
                                               classVisitor,
                                               memberVisitor));
                }
            }
        }

        return multiClassPoolVisitor;
    }


    /**
     * Constructs a ClassPoolVisitor to efficiently travel to the specified
     * classes and class members.
     *
     * @param classSpecifications the list of ClassSpecification instances,
     *                            defining of the classes and class members to
     *                            visit.
     * @param classVisitor        the ClassVisitor to be applied to matching
     *                            classes.
     * @param memberVisitor       the MemberVisitor to be applied to matching
     *                            class members.
     */
    public static ClassPoolVisitor createClassPoolVisitor(List          classSpecifications,
                                                          ClassVisitor  classVisitor,
                                                          MemberVisitor memberVisitor)
    {
        MultiClassPoolVisitor multiClassPoolVisitor = new MultiClassPoolVisitor();

        if (classSpecifications != null)
        {
            for (int index = 0; index < classSpecifications.size(); index++)
            {
                ClassSpecification classSpecification =
                    (ClassSpecification)classSpecifications.get(index);

                multiClassPoolVisitor.addClassPoolVisitor(
                    createClassPoolVisitor(classSpecification,
                                           classVisitor,
                                           memberVisitor));
            }
        }

        return multiClassPoolVisitor;
    }


    /**
     * Constructs a ClassPoolVisitor to efficiently travel to the specified
     * classes and class members.
     *
     * @param keepSpecification the specifications of the class(es) and class
     *                          members to visit.
     * @param classVisitor      the ClassVisitor to be applied to matching
     *                          classes.
     * @param memberVisitor     the MemberVisitor to be applied to matching
     *                          class members.
     */
    private static ClassPoolVisitor createClassPoolVisitor(KeepSpecification keepSpecification,
                                                           ClassVisitor      classVisitor,
                                                           MemberVisitor     memberVisitor)
    {
        // Don't  visit the classes if not specified.
        if (!keepSpecification.markClasses &&
            !keepSpecification.markConditionally)
        {
            classVisitor = null;
        }

        // If specified, let the marker visit the class and its class
        // members conditionally.
        if (keepSpecification.markConditionally)
        {
            // Combine both visitors.
            ClassVisitor composedClassVisitor =
                createCombinedClassVisitor(keepSpecification,
                                           classVisitor,
                                           memberVisitor);

            // Replace the class visitor.
            classVisitor =
                createClassMemberTester(keepSpecification,
                                        composedClassVisitor);

            // Discard the member visitor, because it has already been included.
            memberVisitor = null;
        }

        return createClassPoolVisitor((ClassSpecification)keepSpecification,
                                      classVisitor,
                                      memberVisitor);
    }


    /**
     * Constructs a ClassPoolVisitor to efficiently travel to the specified
     * classes and class members.
     *
     * @param classSpecification the specifications of the class(es) and class
     *                           members to visit.
     * @param classVisitor       the ClassVisitor to be applied to matching
     *                           classes.
     * @param memberVisitor      the MemberVisitor to be applied to matching
     *                           class members.
     */
    private static ClassPoolVisitor createClassPoolVisitor(ClassSpecification classSpecification,
                                                           ClassVisitor       classVisitor,
                                                           MemberVisitor      memberVisitor)
    {
        // Combine both visitors.
        ClassVisitor composedClassVisitor =
            createCombinedClassVisitor(classSpecification,
                                       classVisitor,
                                       memberVisitor);

        // By default, start visiting from the named class name, if specified.
        String className = classSpecification.className;

        // Although we may have to start from the extended class.
        String extendsAnnotationType = classSpecification.extendsAnnotationType;
        String extendsClassName      = classSpecification.extendsClassName;

        // If wildcarded, only visit classes with matching names.
        if (className != null &&
            (extendsAnnotationType != null ||
             extendsClassName           != null ||
             containsWildCards(className)))
        {
            composedClassVisitor =
                new ClassNameFilter(className, composedClassVisitor);

            // We'll have to visit all classes now.
            className = null;
        }

        // If specified, only visit classes with the right annotation.
        String annotationType = classSpecification.annotationType;

        if (annotationType != null)
        {
            composedClassVisitor =
                new AllAttributeVisitor(
                new AllAnnotationVisitor(
                new AnnotationTypeFilter(annotationType,
                new AnnotatedClassVisitor(composedClassVisitor))));
        }

        // If specified, only visit classes with the right access flags.
        if (classSpecification.requiredSetAccessFlags   != 0 ||
            classSpecification.requiredUnsetAccessFlags != 0)
        {
            composedClassVisitor =
                new ClassAccessFilter(classSpecification.requiredSetAccessFlags,
                                      classSpecification.requiredUnsetAccessFlags,
                                      composedClassVisitor);
        }

        // If it's specified, start visiting from the extended class.
        if (extendsAnnotationType != null ||
            extendsClassName      != null)
        {
            // Start visiting from the extended class.
            composedClassVisitor =
                new ClassHierarchyTraveler(false, false, false, true,
                                           composedClassVisitor);

            // If specified, only visit extended classes with the right annotation.
            if (extendsAnnotationType != null)
            {
                composedClassVisitor =
                    new AllAttributeVisitor(
                    new AllAnnotationVisitor(
                    new AnnotationTypeFilter(extendsAnnotationType,
                    new AnnotatedClassVisitor(composedClassVisitor))));
            }

            // If specified, only visit extended classes with matching names.
            if (extendsClassName != null)
            {
                // If wildcarded, only visit extended classes with matching names.
                if (containsWildCards(extendsClassName))
                {
                    composedClassVisitor =
                        new ClassNameFilter(extendsClassName,
                                            composedClassVisitor);
                }
                else
                {
                    // Start visiting from the named extended class.
                    className = extendsClassName;
                }
            }
        }

        // If specified, visit a single named class, otherwise visit all classes.
        return className != null ?
            (ClassPoolVisitor)new NamedClassVisitor(composedClassVisitor, className) :
            (ClassPoolVisitor)new AllClassVisitor(composedClassVisitor);
    }


    /**
     * Constructs a ClassVisitor to efficiently travel to the specified
     * classes and class members.
     *
     * @param classSpecification the specifications of the class(es) and class
     *                           members to visit.
     * @param classVisitor       the ClassVisitor to be applied to matching
     *                           classes.
     * @param memberVisitor      the MemberVisitor to be applied to matching
     *                           class members.
     */
    private static ClassVisitor createCombinedClassVisitor(ClassSpecification classSpecification,
                                                           ClassVisitor       classVisitor,
                                                           MemberVisitor      memberVisitor)
    {
        // Don't visit any members if there aren't any member specifications.
        if (classSpecification.fieldSpecifications  == null &&
            classSpecification.methodSpecifications == null)
        {
            memberVisitor = null;
        }

        // The class visitor for classes and their members.
        MultiClassVisitor multiClassVisitor = new MultiClassVisitor();

        // If specified, let the class visitor visit the class itself.
        if (classVisitor != null)
        {
            // This class visitor may be the only one.
            if (memberVisitor == null)
            {
                return classVisitor;
            }

            multiClassVisitor.addClassVisitor(classVisitor);
        }

        // If specified, let the member info visitor visit the class members.
        if (memberVisitor != null)
        {
            ClassVisitor memberClassVisitor =
                createClassVisitor(classSpecification, memberVisitor);

            // This class visitor may be the only one.
            if (classVisitor == null)
            {
                return memberClassVisitor;
            }

            multiClassVisitor.addClassVisitor(memberClassVisitor);
        }

        return multiClassVisitor;
    }


    /**
     * Constructs a ClassVisitor to efficiently travel to the specified class
     * members.
     *
     * @param classSpecification the specifications of the class members to visit.
     * @param memberVisitor      the MemberVisitor to be applied to matching
     *                           class members.
     */
    private static ClassVisitor createClassVisitor(ClassSpecification classSpecification,
                                                   MemberVisitor      memberVisitor)
    {
        MultiClassVisitor multiClassVisitor = new MultiClassVisitor();

        addMemberVisitors(classSpecification.fieldSpecifications,  true,  multiClassVisitor, memberVisitor);
        addMemberVisitors(classSpecification.methodSpecifications, false, multiClassVisitor, memberVisitor);

        // Mark the class member in this class and in super classes.
        return new ClassHierarchyTraveler(true, true, false, false,
                                          multiClassVisitor);
    }


    /**
     * Adds elements to the given MultiClassVisitor, to apply the given
     * MemberVisitor to all class members that match the given List
     * of options (of the given type).
     */
    private static void addMemberVisitors(List              memberSpecifications,
                                          boolean           isField,
                                          MultiClassVisitor multiClassVisitor,
                                          MemberVisitor     memberVisitor)
    {
        if (memberSpecifications != null)
        {
            for (int index = 0; index < memberSpecifications.size(); index++)
            {
                MemberSpecification memberSpecification =
                    (MemberSpecification)memberSpecifications.get(index);

                multiClassVisitor.addClassVisitor(
                    createClassVisitor(memberSpecification,
                                       isField,
                                       memberVisitor));
            }
        }
    }


    /**
     * Constructs a ClassVisitor that conditionally applies the given
     * ClassVisitor to all classes that contain the given class members.
     */
    private static ClassVisitor createClassMemberTester(ClassSpecification classSpecification,
                                                        ClassVisitor       classVisitor)
    {
        // Create a linked list of conditional visitors, for fields and for
        // methods.
        return createClassMemberTester(classSpecification.fieldSpecifications,
                                       true,
               createClassMemberTester(classSpecification.methodSpecifications,
                                       false,
                                       classVisitor));
    }


    /**
     * Constructs a ClassVisitor that conditionally applies the given
     * ClassVisitor to all classes that contain the given List of class
     * members (of the given type).
     */
    private static ClassVisitor createClassMemberTester(List         memberSpecifications,
                                                        boolean      isField,
                                                        ClassVisitor classVisitor)
    {
        // Create a linked list of conditional visitors.
        if (memberSpecifications != null)
        {
            for (int index = 0; index < memberSpecifications.size(); index++)
            {
                MemberSpecification memberSpecification =
                    (MemberSpecification)memberSpecifications.get(index);

                classVisitor =
                    createClassVisitor(memberSpecification,
                                       isField,
                                       new MemberToClassVisitor(classVisitor));
            }
        }

        return classVisitor;
    }


    /**
     * Creates a new ClassVisitor to efficiently travel to the specified class
     * members.
     *
     * @param memberSpecification the specification of the class member(s) to
     *                            visit.
     * @param memberVisitor       the MemberVisitor to be applied to matching
     *                            class member(s).
     */
    private static ClassVisitor createClassVisitor(MemberSpecification memberSpecification,
                                                   boolean             isField,
                                                   MemberVisitor       memberVisitor)
    {
        String name       = memberSpecification.name;
        String descriptor = memberSpecification.descriptor;

        // If name or descriptor are not fully specified, only visit matching
        // class members.
        boolean fullySpecified =
            name       != null &&
            descriptor != null &&
            !containsWildCards(name) &&
            !containsWildCards(descriptor);

        if (!fullySpecified)
        {
            if (descriptor != null)
            {
                memberVisitor =
                    new MemberDescriptorFilter(descriptor, memberVisitor);
            }

            if (name != null)
            {
                memberVisitor =
                    new MemberNameFilter(name, memberVisitor);
            }
        }

        // If specified, only visit class members with the right annotation.
        if (memberSpecification.annotationType != null)
        {
            memberVisitor =
                new AllAttributeVisitor(
                new AllAnnotationVisitor(
                new AnnotationTypeFilter(memberSpecification.annotationType,
                new AnnotationToMemberVisitor(memberVisitor))));
        }

        // If any access flags are specified, only visit matching class members.
        if (memberSpecification.requiredSetAccessFlags   != 0 ||
            memberSpecification.requiredUnsetAccessFlags != 0)
        {
            memberVisitor =
                new MemberAccessFilter(memberSpecification.requiredSetAccessFlags,
                                       memberSpecification.requiredUnsetAccessFlags,
                                       memberVisitor);
        }

        // Depending on what's specified, visit a single named class member,
        // or all class members, filtering the matching ones.
        return isField ?
            fullySpecified ?
                (ClassVisitor)new NamedFieldVisitor(name, descriptor, memberVisitor) :
                (ClassVisitor)new AllFieldVisitor(memberVisitor) :
            fullySpecified ?
                (ClassVisitor)new NamedMethodVisitor(name, descriptor, memberVisitor) :
                (ClassVisitor)new AllMethodVisitor(memberVisitor);
    }


    // Small utility methods.

    private static boolean containsWildCards(String string)
    {
        return string != null &&
            (string.indexOf('*')   >= 0 ||
             string.indexOf('?')   >= 0 ||
             string.indexOf('%')   >= 0 ||
             string.indexOf(',')   >= 0 ||
             string.indexOf("///") >= 0);
    }
}
