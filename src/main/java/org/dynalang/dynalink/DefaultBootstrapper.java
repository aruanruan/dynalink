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

package org.dynalang.dynalink;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

/**
 * A convenience default bootstrapper that exposes static bootstrap methods which language runtimes that need the very
 * default behavior can use with minimal setup. When first referenced, it will create a dynamic linker with default
 * settings for the {@link DynamicLinkerFactory}, and its bootstrap methods will create {@link MonomorphicCallSite} for
 * all call sites. It has two bootstrap methods: one creates call sites that use the
 * {@link MethodHandles#publicLookup()} as the lookup for all call sites and disregard the one passed in as the caller,
 * and one that just uses the passed caller as the lookup scope. Using the public lookup one is advised if your language
 * runtime has no concept of interacting with Java visibility scopes, as it results in a more lightweight runtime
 * information.
 *
 * @author Attila Szegedi
 */
public class DefaultBootstrapper {
    private static final DynamicLinker dynamicLinker = new DynamicLinkerFactory().createLinker();

    private DefaultBootstrapper() {
    }

    /**
     * Use this method as your bootstrap method (see the documentation of the java.lang.invoke package for how to do
     * this). In case your language runtime doesn't have a concept of interaction with Java access scopes, you might
     * want to consider using
     * {@link #publicBootstrap(java.lang.invoke.MethodHandles.Lookup, String, MethodType)} instead.
     *
     * @param caller the caller's lookup
     * @param name the name of the method at the call site
     * @param type the method signature at the call site
     * @return a new {@link MonomorphicCallSite} linked with the default dynamic linker.
     */
    public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type) {
        return bootstrapInternal(caller, name, type);
    }

    /**
     * Use this method as your bootstrap method (see the documentation of the java.lang.invoke package for how to do
     * this) when your language runtime doesn't have a concept of interaction with Java access scopes. If you need to
     * preserve the different caller Lookup objects in the call sites, use
     * {@link #bootstrap(java.lang.invoke.MethodHandles.Lookup, String, MethodType)} instead
     *
     * @param caller the caller's lookup. It is ignored as the call sites will be created with
     * {@link MethodHandles#publicLookup()} instead.
     * @param name the name of the method at the call site
     * @param type the method signature at the call site
     * @return a new {@link MonomorphicCallSite} linked with the default dynamic linker.
     */
    public static CallSite publicBootstrap(@SuppressWarnings("unused") MethodHandles.Lookup caller, String name, MethodType type) {
        return bootstrapInternal(MethodHandles.publicLookup(), name, type);
    }

    private static CallSite bootstrapInternal(MethodHandles.Lookup caller, String name, MethodType type) {
        return dynamicLinker.link(new MonomorphicCallSite(CallSiteDescriptorFactory.create(caller, name, type)));
    }
}