
package org.qore.test;

import qoremod.DataProvider.Observer;
import qoremod.reflection.Type;

import org.qore.jni.Hash;

class QoreDynamicTest19 extends Observer {
    QoreDynamicTest19() throws Throwable {
    }

    public String get(Type type) throws Throwable {
        return type.getName();
    }

    public void update(String event_id, Hash data_) {
        // noop
    }
}