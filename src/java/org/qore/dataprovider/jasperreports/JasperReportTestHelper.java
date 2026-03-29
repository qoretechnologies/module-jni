/*  JasperReportTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

package org.qore.dataprovider.jasperreports;

import java.nio.charset.StandardCharsets;

/**
 * Helper class for creating test JRXML templates for JasperReports data provider tests.
 */
public class JasperReportTestHelper {

    /**
     * Returns a simple JRXML template string with three fields: name (String),
     * age (Integer), and email (String).
     * The template includes a title band and a detail band for rendering data.
     *
     * @return A valid JRXML template string
     */
    public static String getSimpleJrxml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"\n"
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports"
            + " http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"\n"
            + "    name=\"TestReport\" pageWidth=\"595\" pageHeight=\"842\" columnWidth=\"555\"\n"
            + "    leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">\n"
            + "    <field name=\"name\" class=\"java.lang.String\"/>\n"
            + "    <field name=\"age\" class=\"java.lang.Integer\"/>\n"
            + "    <field name=\"email\" class=\"java.lang.String\"/>\n"
            + "    <title>\n"
            + "        <band height=\"30\">\n"
            + "            <staticText>\n"
            + "                <reportElement x=\"0\" y=\"0\" width=\"555\" height=\"30\"/>\n"
            + "                <textElement textAlignment=\"Center\">\n"
            + "                    <font size=\"16\" isBold=\"true\"/>\n"
            + "                </textElement>\n"
            + "                <text><![CDATA[Test Report]]></text>\n"
            + "            </staticText>\n"
            + "        </band>\n"
            + "    </title>\n"
            + "    <detail>\n"
            + "        <band height=\"20\">\n"
            + "            <textField>\n"
            + "                <reportElement x=\"0\" y=\"0\" width=\"185\" height=\"20\"/>\n"
            + "                <textFieldExpression><![CDATA[$F{name}]]></textFieldExpression>\n"
            + "            </textField>\n"
            + "            <textField>\n"
            + "                <reportElement x=\"185\" y=\"0\" width=\"185\" height=\"20\"/>\n"
            + "                <textFieldExpression><![CDATA[$F{age}]]></textFieldExpression>\n"
            + "            </textField>\n"
            + "            <textField>\n"
            + "                <reportElement x=\"370\" y=\"0\" width=\"185\" height=\"20\"/>\n"
            + "                <textFieldExpression><![CDATA[$F{email}]]></textFieldExpression>\n"
            + "            </textField>\n"
            + "        </band>\n"
            + "    </detail>\n"
            + "</jasperReport>\n";
    }

    /**
     * Returns the simple JRXML template as UTF-8 encoded bytes.
     *
     * @return The JRXML template as a byte array
     */
    public static byte[] getSimpleJrxmlBytes() {
        return getSimpleJrxml().getBytes(StandardCharsets.UTF_8);
    }
}
