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

import proguard.classfile.Clazz;

/**
 * This class stores some optimization information that can be attached to
 * a class.
 *
 * @author Eric Lafortune
 */
public class ClassOptimizationInfo
{
    private boolean isInstantiated                = false;
    private boolean isInstanceofed                = false;
    private boolean isDotClassed                  = false;
    private boolean containsPackageVisibleMembers = false;
    private boolean invokesPackageVisibleMembers  = false;
    private Clazz   targetClass;


    public void setInstantiated()
    {
        isInstantiated = true;
    }


    public boolean isInstantiated()
    {
        return isInstantiated;
    }


    public void setInstanceofed()
    {
        isInstanceofed = true;
    }


    public boolean isInstanceofed()
    {
        return isInstanceofed;
    }


    public void setDotClassed()
    {
        isDotClassed = true;
    }


    public boolean isDotClassed()
    {
        return isDotClassed;
    }


    public void setContainsPackageVisibleMembers()
    {
        containsPackageVisibleMembers = true;
    }


    public boolean containsPackageVisibleMembers()
    {
        return containsPackageVisibleMembers;
    }


    public void setInvokesPackageVisibleMembers()
    {
        invokesPackageVisibleMembers = true;
    }


    public boolean invokesPackageVisibleMembers()
    {
        return invokesPackageVisibleMembers;
    }


    public void setTargetClass(Clazz targetClass)
    {
        this.targetClass = targetClass;
    }


    public Clazz getTargetClass()
    {
        return targetClass;
    }


    public static void setClassOptimizationInfo(Clazz clazz)
    {
        clazz.setVisitorInfo(new ClassOptimizationInfo());
    }


    public static ClassOptimizationInfo getClassOptimizationInfo(Clazz clazz)
    {
        Object visitorInfo = clazz.getVisitorInfo();

        return visitorInfo instanceof ClassOptimizationInfo ?
            (ClassOptimizationInfo)visitorInfo :
            null;
    }
}
