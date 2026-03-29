/*  VcfTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import ezvcard.property.Birthday;
import ezvcard.property.Categories;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Nickname;
import ezvcard.property.Note;
import ezvcard.property.Organization;
import ezvcard.property.Role;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.Title;
import ezvcard.property.Uid;
import ezvcard.property.Url;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for creating test VCF files for the VcfDataProvider tests.
 */
public class VcfTestHelper {

    /**
     * Creates a simple VCF file with 2 contacts.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createSimpleVcf(String path) throws IOException {
        List<VCard> vcards = createSimpleVcards();

        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
        }
    }

    /**
     * Creates a simple VCF with 2 contacts and returns the bytes.
     *
     * @return The VCF file as bytes
     * @throws IOException if an I/O error occurs
     */
    public static byte[] createSimpleVcfBytes() throws IOException {
        List<VCard> vcards = createSimpleVcards();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
            return out.toByteArray();
        }
    }

    /**
     * Creates a list of 2 simple VCard objects for testing.
     */
    private static List<VCard> createSimpleVcards() {
        List<VCard> vcards = new ArrayList<>();

        // Contact 1: Alice Smith
        VCard alice = new VCard();
        alice.setFormattedName("Alice Smith");
        StructuredName snAlice = new StructuredName();
        snAlice.setGiven("Alice");
        snAlice.setFamily("Smith");
        alice.setStructuredName(snAlice);

        Email aliceEmail = new Email("alice@example.com");
        aliceEmail.getTypes().add(EmailType.WORK);
        alice.addEmail(aliceEmail);

        Telephone alicePhone = new Telephone("+1-555-0101");
        alicePhone.getTypes().add(TelephoneType.CELL);
        alice.addTelephoneNumber(alicePhone);

        vcards.add(alice);

        // Contact 2: Bob Johnson
        VCard bob = new VCard();
        bob.setFormattedName("Bob Johnson");
        StructuredName snBob = new StructuredName();
        snBob.setGiven("Bob");
        snBob.setFamily("Johnson");
        bob.setStructuredName(snBob);

        Email bobEmail = new Email("bob@example.com");
        bobEmail.getTypes().add(EmailType.HOME);
        bob.addEmail(bobEmail);

        Telephone bobPhone = new Telephone("+1-555-0102");
        bobPhone.getTypes().add(TelephoneType.WORK);
        bob.addTelephoneNumber(bobPhone);

        vcards.add(bob);

        return vcards;
    }

    /**
     * Creates a detailed VCF file with all fields populated for one contact.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createDetailedVcf(String path) throws IOException {
        List<VCard> vcards = new ArrayList<>();

        VCard card = new VCard();

        // Formatted name
        card.setFormattedName("Dr. Jane Marie Wilson");

        // Structured name
        StructuredName sn = new StructuredName();
        sn.setGiven("Jane");
        sn.setFamily("Wilson");
        card.setStructuredName(sn);

        // Multiple emails
        Email workEmail = new Email("jane.wilson@corp.example.com");
        workEmail.getTypes().add(EmailType.WORK);
        card.addEmail(workEmail);

        Email homeEmail = new Email("jane@personal.example.com");
        homeEmail.getTypes().add(EmailType.HOME);
        card.addEmail(homeEmail);

        // Multiple phones
        Telephone workPhone = new Telephone("+1-555-0201");
        workPhone.getTypes().add(TelephoneType.WORK);
        card.addTelephoneNumber(workPhone);

        Telephone cellPhone = new Telephone("+1-555-0202");
        cellPhone.getTypes().add(TelephoneType.CELL);
        card.addTelephoneNumber(cellPhone);

        Telephone homePhone = new Telephone("+1-555-0203");
        homePhone.getTypes().add(TelephoneType.HOME);
        card.addTelephoneNumber(homePhone);

        // Multiple addresses
        Address workAddr = new Address();
        workAddr.getTypes().add(AddressType.WORK);
        workAddr.setStreetAddress("100 Corporate Blvd");
        workAddr.setLocality("San Francisco");
        workAddr.setRegion("CA");
        workAddr.setPostalCode("94105");
        workAddr.setCountry("USA");
        card.addAddress(workAddr);

        Address homeAddr = new Address();
        homeAddr.getTypes().add(AddressType.HOME);
        homeAddr.setStreetAddress("42 Oak Street");
        homeAddr.setLocality("Berkeley");
        homeAddr.setRegion("CA");
        homeAddr.setPostalCode("94702");
        homeAddr.setCountry("USA");
        card.addAddress(homeAddr);

        // Organization
        Organization org = new Organization();
        org.getValues().add("Acme Corporation");
        card.setOrganization(org);

        // Title
        card.addTitle("Vice President of Engineering");

        // Note
        card.addNote("Key contact for the West Coast division. Prefers email communication.");

        // UID
        card.setUid(new Uid("urn:uuid:12345678-1234-1234-1234-123456789abc"));

        vcards.add(card);

        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
        }
    }

    /**
     * Creates an empty VCF file with no contacts.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createEmptyVcf(String path) throws IOException {
        List<VCard> vcards = new ArrayList<>();

        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
        }
    }

    /**
     * Creates a VCF file with all fields populated including birthday, url, nickname,
     * categories, and role.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createFullFieldsVcf(String path) throws IOException {
        List<VCard> vcards = new ArrayList<>();
        vcards.add(createFullFieldsVcard());

        try (FileOutputStream out = new FileOutputStream(new File(path))) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
        }
    }

    /**
     * Creates a VCF with all fields populated and returns the bytes.
     *
     * @return The VCF file as bytes
     * @throws IOException if an I/O error occurs
     */
    public static byte[] createFullFieldsVcfBytes() throws IOException {
        List<VCard> vcards = new ArrayList<>();
        vcards.add(createFullFieldsVcard());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Ezvcard.write(vcards).version(VCardVersion.V3_0).go(out);
            return out.toByteArray();
        }
    }

    /**
     * Creates a VCard with all fields populated including the new fields:
     * birthday, url, nickname, categories, and role.
     */
    private static VCard createFullFieldsVcard() {
        VCard card = new VCard();

        // Formatted name
        card.setFormattedName("Charlie Full Fields");

        // Structured name
        StructuredName sn = new StructuredName();
        sn.setGiven("Charlie");
        sn.setFamily("Full Fields");
        card.setStructuredName(sn);

        // Email
        Email email = new Email("charlie@example.com");
        email.getTypes().add(EmailType.WORK);
        card.addEmail(email);

        // Phone
        Telephone phone = new Telephone("+1-555-0300");
        phone.getTypes().add(TelephoneType.CELL);
        card.addTelephoneNumber(phone);

        // Address
        Address addr = new Address();
        addr.getTypes().add(AddressType.HOME);
        addr.setStreetAddress("789 Test Lane");
        addr.setLocality("Portland");
        addr.setRegion("OR");
        addr.setPostalCode("97201");
        addr.setCountry("USA");
        card.addAddress(addr);

        // Organization
        Organization org = new Organization();
        org.getValues().add("Full Fields Corp");
        card.setOrganization(org);

        // Title
        card.addTitle("Chief Testing Officer");

        // Note
        card.addNote("Contact with all fields populated for testing");

        // UID
        card.setUid(new Uid("urn:uuid:full-fields-test-1234"));

        // Birthday - 1990-06-15
        card.setBirthday(new Birthday(LocalDate.of(1990, 6, 15)));

        // URL
        card.addUrl("https://charlie.example.com");

        // Nickname
        Nickname nick = new Nickname();
        nick.getValues().add("Chuck");
        card.setNickname(nick);

        // Categories
        Categories cats = new Categories();
        cats.getValues().add("friend");
        cats.getValues().add("colleague");
        cats.getValues().add("vip");
        card.setCategories(cats);

        // Role
        card.addRole("Quality Assurance Lead");

        return card;
    }
}
