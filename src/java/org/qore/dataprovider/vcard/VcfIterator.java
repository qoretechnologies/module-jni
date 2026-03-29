/*  VcfIterator.java Copyright 2026 Qore Technologies, s.r.o.

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
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.Organization;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading vCard (.vcf) files.
 * Supports multi-contact VCF files using ez-vcard library.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class VcfIterator extends qore.Qore.AbstractIterator implements java.io.Closeable {
    private List<VCard> vcards;
    private int currentIndex = -1;
    private Hash currentValue = null;
    private long count = 0;

    /**
     * Creates a VcfIterator from a Java InputStream.
     *
     * @param stream The input stream containing VCF data
     * @throws Throwable if an error occurs reading or parsing VCF data
     */
    public VcfIterator(InputStream stream) throws Throwable {
        try {
            vcards = Ezvcard.parse(stream).all();
        } finally {
            stream.close();
        }
    }

    /**
     * Creates a VcfIterator from a Qore InputStream.
     *
     * @param stream The Qore input stream containing VCF data
     * @throws Throwable if an error occurs reading or parsing VCF data
     */
    public VcfIterator(qore.Qore.InputStream stream) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream));
    }

    /**
     * Creates a VcfIterator from a file path.
     *
     * @param path The path to the VCF file
     * @throws Throwable if an error occurs reading or parsing VCF data
     */
    public VcfIterator(String path) throws Throwable {
        this(new FileInputStream(new File(path)));
    }

    /**
     * Creates a VcfIterator from raw byte data.
     *
     * @param data The VCF data as bytes
     * @throws Throwable if an error occurs reading or parsing VCF data
     */
    public VcfIterator(byte[] data) throws Throwable {
        this(new java.io.ByteArrayInputStream(data));
    }

    /**
     * Returns the number of contacts in the VCF data.
     *
     * @return the number of contacts
     */
    public int getContactCount() {
        return vcards != null ? vcards.size() : 0;
    }

    /**
     * Returns the number of contacts iterated so far.
     *
     * @return the count of iterated contacts
     */
    public long getCount() {
        return count;
    }

    @Override
    public boolean next() {
        if (vcards == null || vcards.isEmpty()) {
            currentValue = null;
            return false;
        }

        ++currentIndex;
        if (currentIndex >= vcards.size()) {
            currentValue = null;
            return false;
        }

        currentValue = vcardToHash(vcards.get(currentIndex));
        ++count;
        return true;
    }

    @Override
    public Hash getValue() throws QoreException {
        if (currentValue == null) {
            throw new QoreException("INVALID-ITERATOR", "iterator is not valid; next() must return true before " +
                "calling this method");
        }
        return currentValue;
    }

    @Override
    public boolean valid() {
        return currentValue != null;
    }

    /**
     * Converts a VCard object to a Hash with the standard contact schema.
     *
     * @param vcard The VCard to convert
     * @return A Hash containing the contact fields
     */
    static Hash vcardToHash(VCard vcard) {
        Hash h = new Hash();

        // formatted_name (required)
        if (vcard.getFormattedName() != null) {
            h.put("formatted_name", vcard.getFormattedName().getValue());
        } else {
            h.put("formatted_name", "");
        }

        // given_name and family_name
        StructuredName sn = vcard.getStructuredName();
        if (sn != null) {
            h.put("given_name", sn.getGiven());
            h.put("family_name", sn.getFamily());
        } else {
            h.put("given_name", null);
            h.put("family_name", null);
        }

        // emails
        List<Email> emails = vcard.getEmails();
        if (emails != null && !emails.isEmpty()) {
            ArrayList<Hash> emailList = new ArrayList<>();
            for (Email email : emails) {
                Hash emailHash = new Hash();
                // Get type from parameters
                List<EmailType> types = email.getTypes();
                if (types != null && !types.isEmpty()) {
                    emailHash.put("type", types.get(0).getValue());
                } else {
                    emailHash.put("type", "internet");
                }
                emailHash.put("value", email.getValue());
                emailList.add(emailHash);
            }
            h.put("emails", emailList);
        } else {
            h.put("emails", null);
        }

        // phones
        List<Telephone> phones = vcard.getTelephoneNumbers();
        if (phones != null && !phones.isEmpty()) {
            ArrayList<Hash> phoneList = new ArrayList<>();
            for (Telephone phone : phones) {
                Hash phoneHash = new Hash();
                List<TelephoneType> types = phone.getTypes();
                if (types != null && !types.isEmpty()) {
                    phoneHash.put("type", types.get(0).getValue());
                } else {
                    phoneHash.put("type", "voice");
                }
                phoneHash.put("value", phone.getText());
                phoneList.add(phoneHash);
            }
            h.put("phones", phoneList);
        } else {
            h.put("phones", null);
        }

        // addresses
        List<Address> addresses = vcard.getAddresses();
        if (addresses != null && !addresses.isEmpty()) {
            ArrayList<Hash> addrList = new ArrayList<>();
            for (Address addr : addresses) {
                Hash addrHash = new Hash();
                List<AddressType> types = addr.getTypes();
                if (types != null && !types.isEmpty()) {
                    addrHash.put("type", types.get(0).getValue());
                } else {
                    addrHash.put("type", "home");
                }
                addrHash.put("street", addr.getStreetAddress());
                addrHash.put("city", addr.getLocality());
                addrHash.put("state", addr.getRegion());
                addrHash.put("postal_code", addr.getPostalCode());
                addrHash.put("country", addr.getCountry());
                addrList.add(addrHash);
            }
            h.put("addresses", addrList);
        } else {
            h.put("addresses", null);
        }

        // organization
        Organization org = vcard.getOrganization();
        if (org != null && org.getValues() != null && !org.getValues().isEmpty()) {
            h.put("organization", org.getValues().get(0));
        } else {
            h.put("organization", null);
        }

        // title
        if (vcard.getTitles() != null && !vcard.getTitles().isEmpty()) {
            h.put("title", vcard.getTitles().get(0).getValue());
        } else {
            h.put("title", null);
        }

        // note
        if (vcard.getNotes() != null && !vcard.getNotes().isEmpty()) {
            h.put("note", vcard.getNotes().get(0).getValue());
        } else {
            h.put("note", null);
        }

        // uid
        if (vcard.getUid() != null) {
            h.put("uid", vcard.getUid().getValue());
        } else {
            h.put("uid", null);
        }

        return h;
    }

    /**
     * Closes the iterator and releases resources.
     */
    @Override
    public void close() throws IOException {
        vcards = null;
        currentValue = null;
    }
}
