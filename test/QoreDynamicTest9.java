
package org.qore.test;

import pythonmod.json.encoder.JSONEncoder;
import org.qore.jni.Hash;

public class QoreDynamicTest9 extends JSONEncoder {
    public QoreDynamicTest9() throws Throwable {
    }

    public String test(Hash h) throws Throwable {
        return (String)encode(h);
    }
}
