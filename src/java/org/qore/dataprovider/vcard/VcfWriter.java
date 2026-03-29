/*  VcfWriter.java Copyright 2026 Qore Technologies, s.r.o.

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

package org.qore.dataprovider.vcard;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Note;
import ezvcard.property.Organization;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.Title;
import ezvcard.property.Uid;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.qore.jni.Hash;

/**
 * Writer for creating vCard (.vcf) files.
 * Accumulates VCard objects and writes them all at once.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class VcfWriter implements Closeable {
    private ArrayList<VCard> vcards = new ArrayList<>();

    /**
     * Creates a new VcfWriter.
     */
    public VcfWriter() {
    }

    /**
     * Writes a contact from a Hash to the internal VCard list.
     *
     * @param data The contact data as a Hash
     */
    @SuppressWarnings("unchecked")
    public void writeContact(Hash data) {
        VCard vcard = new VCard();

        // formatted_name (required)
        Object fnObj = data.get("formatted_name");
        if (fnObj != null) {
            vcard.setFormattedName(fnObj.toString());
        }

        // given_name and family_name
        Object givenObj = data.get("given_name");
        Object familyObj = data.get("family_name");
        if (givenObj != null || familyObj != null) {
            StructuredName sn = new StructuredName();
            if (givenObj != null) {
                sn.setGiven(givenObj.toString());
            }
            if (familyObj != null) {
                sn.setFamily(familyObj.toString());
            }
            vcard.setStructuredName(sn);
        }

        // emails
        Object emailsObj = data.get("emails");
        if (emailsObj != null) {
            for (Object item : toIterable(emailsObj)) {
                Hash emailHash = toHash(item);
                if (emailHash != null) {
                    Email email = new Email(getStringValue(emailHash, "value"));
                    String type = getStringValue(emailHash, "type");
                    if (type != null && !type.isEmpty()) {
                        email.getTypes().add(EmailType.get(type));
                    }
                    vcard.addEmail(email);
                }
            }
        }

        // phones
        Object phonesObj = data.get("phones");
        if (phonesObj != null) {
            for (Object item : toIterable(phonesObj)) {
                Hash phoneHash = toHash(item);
                if (phoneHash != null) {
                    Telephone phone = new Telephone(getStringValue(phoneHash, "value"));
                    String type = getStringValue(phoneHash, "type");
                    if (type != null && !type.isEmpty()) {
                        phone.getTypes().add(TelephoneType.get(type));
                    }
                    vcard.addTelephoneNumber(phone);
                }
            }
        }

        // addresses
        Object addrsObj = data.get("addresses");
        if (addrsObj != null) {
            for (Object item : toIterable(addrsObj)) {
                Hash addrHash = toHash(item);
                if (addrHash != null) {
                    Address addr = new Address();
                    String type = getStringValue(addrHash, "type");
                    if (type != null && !type.isEmpty()) {
                        addr.getTypes().add(AddressType.get(type));
                    }
                    String street = getStringValue(addrHash, "street");
                    if (street != null) {
                        addr.setStreetAddress(street);
                    }
                    String city = getStringValue(addrHash, "city");
                    if (city != null) {
                        addr.setLocality(city);
                    }
                    String state = getStringValue(addrHash, "state");
                    if (state != null) {
                        addr.setRegion(state);
                    }
                    String postalCode = getStringValue(addrHash, "postal_code");
                    if (postalCode != null) {
                        addr.setPostalCode(postalCode);
                    }
                    String country = getStringValue(addrHash, "country");
                    if (country != null) {
                        addr.setCountry(country);
                    }
                    vcard.addAddress(addr);
                }
            }
        }

        // organization
        Object orgObj = data.get("organization");
        if (orgObj != null) {
            Organization org = new Organization();
            org.getValues().add(orgObj.toString());
            vcard.setOrganization(org);
        }

        // title
        Object titleObj = data.get("title");
        if (titleObj != null) {
            vcard.addTitle(titleObj.toString());
        }

        // note
        Object noteObj = data.get("note");
        if (noteObj != null) {
            vcard.addNote(noteObj.toString());
        }

        // uid
        Object uidObj = data.get("uid");
        if (uidObj != null) {
            vcard.setUid(new Uid(uidObj.toString()));
        }

        vcards.add(vcard);
    }

    /**
     * Gets a string value from a Hash, returning null if not found.
     */
    private static String getStringValue(Hash hash, String key) {
        Object val = hash.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Converts an object to an iterable of Objects.
     * Handles both Object[] arrays (from Qore lists) and java.util.List.
     *
     * @param obj The object to convert
     * @return An iterable of objects
     */
    private static Iterable<Object> toIterable(Object obj) {
        if (obj instanceof Object[]) {
            return java.util.Arrays.asList((Object[]) obj);
        } else if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            return list;
        }
        // Single item
        return java.util.Collections.singletonList(obj);
    }

    /**
     * Converts an object to a Hash.
     * Handles Hash objects directly and Map objects.
     *
     * @param obj The object to convert
     * @return A Hash, or null if conversion is not possible
     */
    private static Hash toHash(Object obj) {
        if (obj instanceof Hash) {
            return (Hash) obj;
        } else if (obj instanceof Map) {
            Hash h = new Hash();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                h.put(entry.getKey().toString(), entry.getValue());
            }
            return h;
        }
        return null;
    }

    /**
     * Writes all accumulated VCards to a file.
     *
     * @param path The file path to write to
     * @throws IOException if an I/O error occurs
     */
    public void writeToFile(String path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
        }
    }

    /**
     * Writes all accumulated VCards to an output stream.
     *
     * @param stream The output stream to write to
     * @throws IOException if an I/O error occurs
     */
    public void writeToStream(OutputStream stream) throws IOException {
        Ezvcard.write(vcards).version(VCardVersion.V3_0).go(stream);
    }

    /**
     * Writes all accumulated VCards to a Qore output stream.
     *
     * @param stream The Qore output stream to write to
     * @throws IOException if an I/O error occurs
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns all accumulated VCards as bytes.
     *
     * @return The VCF data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public byte[] getBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
            return out.toByteArray();
        }
    }

    /**
     * Returns the number of contacts accumulated.
     *
     * @return the contact count
     */
    public int getContactCount() {
        return vcards.size();
    }

    /**
     * Closes the writer and releases resources.
     */
    @Override
    public void close() throws IOException {
        vcards = null;
    }
}
