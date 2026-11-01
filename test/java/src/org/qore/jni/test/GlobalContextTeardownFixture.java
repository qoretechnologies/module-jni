// Copyright (C) 2026 Qore Technologies, s.r.o.
package org.qore.jni.test;

import org.qore.jni.QoreJavaApi;
import org.qore.jni.QoreURLClassLoader;

/** Makes the jni module's global context Program hold a Qore object until the module is deleted
 *
 * A Java thread that does not run in a Qore Program uses the global context Program, which the jni module creates
 * and deletes only when the module is deleted at process exit; the objects held by its data are destroyed then.
 */
public final class GlobalContextTeardownFixture {
    private GlobalContextTeardownFixture() {
    }

    /** Loads the given Qore module into the global context Program and calls the given function there
     *
     * The calls are made from a new Java thread whose class loader context is the global context Program, which
     * the function can make hold an object (ex: in a class static variable of the module)
     *
     * @param module the Qore module to load
     * @param function the Qore function to call after the module is loaded
     * @throws Throwable any exception raised by the calls
     */
    public static void holdInGlobalContext(String module, String function) throws Throwable {
        Throwable[] error = new Throwable[1];
        Thread thread = new Thread(() -> {
            QoreURLClassLoader loader = new QoreURLClassLoader();
            try {
                QoreJavaApi.callFunction("load_module", module);
                QoreJavaApi.callFunction(function);
            } catch (Throwable e) {
                error[0] = e;
            } finally {
                loader.clearContext();
            }
        });
        thread.start();
        thread.join();
        if (error[0] != null) {
            throw error[0];
        }
    }
}
