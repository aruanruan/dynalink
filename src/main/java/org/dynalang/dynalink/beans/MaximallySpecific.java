/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.linker.ConversionComparator.Comparison;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Utility class that encapsulates the algorithm for choosing the maximally specific methods.
 *
 * @author Attila Szegedi
 */
class MaximallySpecific {
    /**
     * Given a list of methods, returns a list of maximally specific methods.
     *
     * @param methods the list of methods
     * @param varArgs whether to assume the methods are varargs
     * @return the list of maximally specific methods.
     */
    static List<MethodHandle> getMaximallySpecificMethods(List<MethodHandle> methods, boolean varArgs) {
        return getMaximallySpecificMethods(methods, varArgs, null, null);
    }

    /**
     * Given a list of methods, returns a list of maximally specific methods, applying language-runtime specific
     * conversion preferences.
     *
     * @param methods the list of methods
     * @param varArgs whether to assume the methods are varargs
     * @param argTypes concrete argument types for the invocation
     * @return the list of maximally specific methods.
     */
    static List<MethodHandle> getMaximallySpecificMethods(List<MethodHandle> methods, boolean varArgs,
            Class<?>[] argTypes, LinkerServices ls) {
        if(methods.size() < 2) {
            return methods;
        }
        final LinkedList<MethodHandle> maximals = new LinkedList<>();
        for(MethodHandle m: methods) {
            final MethodType methodType = m.type();
            boolean lessSpecific = false;
            for(Iterator<MethodHandle> maximal = maximals.iterator(); maximal.hasNext();) {
                final MethodHandle max = maximal.next();
                switch(isMoreSpecific(methodType, max.type(), varArgs, argTypes, ls)) {
                    case TYPE_1_BETTER: {
                        maximal.remove();
                        break;
                    }
                    case TYPE_2_BETTER: {
                        lessSpecific = true;
                        break;
                    }
                    case INDETERMINATE: {
                        // do nothing
                        break;
                    }
                    default: {
                        throw new AssertionError();
                    }
                }
            }
            if(!lessSpecific) {
                maximals.addLast(m);
            }
        }
        return maximals;
    }

    private static Comparison isMoreSpecific(MethodType t1, MethodType t2, boolean varArgs, Class<?>[] argTypes,
            LinkerServices ls) {
        final int pc1 = t1.parameterCount();
        final int pc2 = t2.parameterCount();
        assert varArgs || (pc1 == pc2) && (argTypes == null || argTypes.length == pc1);
        assert (argTypes == null) == (ls == null);
        final int maxPc = Math.max(Math.max(pc1, pc2), argTypes == null ? 0 : argTypes.length);
        boolean t1MoreSpecific = false;
        boolean t2MoreSpecific = false;
        // NOTE: Starting from 1 as overloaded method resolution doesn't depend on 0th element, which is the type of
        // 'this'. We're only dealing with instance methods here, not static methods. Actually, static methods will have
        // a fake 'this' of type StaticClass.
        for(int i = 1; i < maxPc; ++i) {
            final Class<?> c1 = getParameterClass(t1, pc1, i, varArgs);
            final Class<?> c2 = getParameterClass(t2, pc2, i, varArgs);
            if(c1 != c2) {
                final Comparison cmp = compare(c1, c2, argTypes, i, ls);
                if(cmp == Comparison.TYPE_1_BETTER && !t1MoreSpecific) {
                    t1MoreSpecific = true;
                    if(t2MoreSpecific) {
                        return Comparison.INDETERMINATE;
                    }
                }
                if(cmp == Comparison.TYPE_2_BETTER && !t2MoreSpecific) {
                    t2MoreSpecific = true;
                    if(t1MoreSpecific) {
                        return Comparison.INDETERMINATE;
                    }
                }
            }
        }
        if(t1MoreSpecific) {
            return Comparison.TYPE_1_BETTER;
        } else if(t2MoreSpecific) {
            return Comparison.TYPE_2_BETTER;
        }
        return Comparison.INDETERMINATE;
    }

    private static Comparison compare(Class<?> c1, Class<?> c2, Class<?>[] argTypes, int i, LinkerServices cmp) {
        if(cmp != null) {
            final Comparison c = cmp.compareConversion(argTypes[i], c1, c2);
            if(c != Comparison.INDETERMINATE) {
                return c;
            }
        }
        if(TypeUtilities.isSubtype(c1, c2)) {
            return Comparison.TYPE_1_BETTER;
        } if(TypeUtilities.isSubtype(c2, c1)) {
            return Comparison.TYPE_2_BETTER;
        }
        return Comparison.INDETERMINATE;
    }

    private static Class<?> getParameterClass(MethodType t, int l, int i, boolean varArgs) {
        return varArgs && i >= l - 1 ? t.parameterType(l - 1).getComponentType() : t.parameterType(i);
    }
}
