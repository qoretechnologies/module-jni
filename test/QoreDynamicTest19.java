
package org.qore.test;

import qoremod.DataProvider.Observer;
import qoremod.reflection.Type;

import java.util.Map;

@SuppressWarnings("rawtypes")
class QoreDynamicTest19 extends Observer {
    QoreDynamicTest19() throws Throwable {
    }

    public String get(Type type) throws Throwable {
        return type.getName();
    }

    public void update(String event_id, Map data_) {
        // noop
    }
}