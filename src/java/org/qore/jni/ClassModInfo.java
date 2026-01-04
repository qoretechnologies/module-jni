/*
    ClassModInfo.java

    Qore Programming Language JNI Module

    Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

package org.qore.jni;

class ClassModInfo {
    public String cls;
    public String mod;
    public boolean python;
    public boolean kotlin;

    ClassModInfo(String bin_name) {
        init(bin_name, false);
    }

    ClassModInfo(String bin_name, boolean is_package) {
        init(bin_name, is_package);
    }

    private void init(String bin_name, boolean is_package) {
        //System.out.printf("ClassModInfo(%s)'\n", bin_name);
        //Thread.dumpStack();

        mod = null;
        cls = "::";
        python = false;
        kotlin = false;
        if (bin_name.equals("qore") || bin_name.equals("python") || bin_name.equals("kotlin")) {
            return;
        } else if (bin_name.startsWith("qore.")) {
            cls += bin_name.substring(5);
        } else if (bin_name.startsWith("qoremod.")) {
            int end = bin_name.indexOf(".", 9);
            if (end >= 9 && end < (bin_name.length() - 1)) {
                mod = bin_name.substring(8, end);
                cls = bin_name.substring(end + 1);
            } else {
                mod = bin_name.substring(8);
                cls = null;
            }
            if (mod.equals("python")) {
                python = true;
                cls = "Python::" + cls;
                mod = null;
            }
        } else if (bin_name.startsWith("python.")) {
            python = true;
            cls += "Python::";
            cls += bin_name.substring(7);
        } else if (bin_name.startsWith("pythonmod.")) {
            python = true;
            int end = is_package
                ? bin_name.lastIndexOf(".")
                : bin_name.indexOf(".", 11);
            if (end >= 11 && end < (bin_name.length() - 1)) {
                mod = bin_name.substring(10, end);
                cls = bin_name.substring(end + 1);
            } else {
                mod = bin_name.substring(10);
                cls = null;
                //return;
            }
        } else if (bin_name.startsWith("kotlin.")) {
            kotlin = true;
            cls += "Kotlin::";
            cls += bin_name.substring(7);
        } else if (bin_name.startsWith("kotlinmod.")) {
            kotlin = true;
            int end = is_package
                ? bin_name.lastIndexOf(".")
                : bin_name.indexOf(".", 11);
            if (end >= 11 && end < (bin_name.length() - 1)) {
                mod = bin_name.substring(10, end);
                cls = bin_name.substring(end + 1);
            } else {
                mod = bin_name.substring(10);
                cls = null;
            }
        } else {
            cls += bin_name;
        }
        if (cls != null) {
            cls = cls.replaceAll("\\.", "::");
        }
        //System.out.printf("ClassModInfo (pkg: %s): %s => %s\n", is_package, bin_name, this);
    }

    @Override
    public String toString() {
        return String.format("ClassModInfo{cls=%s, mod=%s, python=%s, kotlin=%s}", cls, mod, python, kotlin);
    }
}