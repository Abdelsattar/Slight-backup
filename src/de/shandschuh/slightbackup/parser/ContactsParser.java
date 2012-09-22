/**
 * Slight backup - a simple backup tool
 *
 * Copyright (c) 2012 Stefan Handschuh
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package de.shandschuh.slightbackup.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.SAXException;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import de.shandschuh.slightbackup.BackupActivity;
import de.shandschuh.slightbackup.R;
import de.shandschuh.slightbackup.Strings;
import de.shandschuh.slightbackup.exporter.ContactsExporter;

public class ContactsParser extends SimpleParser {
	public static final String NAME = Strings.CONTACTS;
	
	public static final int NAMEID = R.string.contacts;
	
	private static final String NEWLINE_REGEX = "[\\n]";
	
	private static final String TEMPFILE_NAME = ".__slight_backup__tmp__.vcf";
	
	private static final String VCARD_TYPE = "text/x-vcard";
	
	private StringBuilder vcardStringBuilder;
	
	private File vcardsFile;
	
	private FileOutputStream vcardsFileOutputStream;
	
	private Set<String> existingVcards;
	
	private boolean hasEntries;
	
	public ContactsParser(Context context, ImportTask importTask) {
		super(context, Strings.TAG_CONTACT, new String[] {ContactsExporter.LOOKUP_FIELDNAME}, ContactsExporter.CONTACTS_URI, importTask);
		vcardsFile = new File(new StringBuilder(BackupActivity.DIR.toString()).append('/').append(TEMPFILE_NAME).toString());
		if (vcardsFile.exists()) {
			vcardsFile.delete(); // we are overwriting soon, but let's delete it anyway
		}
		hasEntries = false;
	}
	
	@Override
	public void startDocument() throws SAXException {
		existingVcards = new HashSet<String>();
		
		Cursor contactsCursor = context.getContentResolver().query(ContactsExporter.CONTACTS_URI, new String[] {ContactsExporter.LOOKUP_FIELDNAME}, null, null, null);
		
		while (contactsCursor.moveToNext()) {
			try {
				String vcard = new String(ContactsExporter.getVcardBytes(context, contactsCursor.getString(0))).replace("\r\n", "\n").trim();
				
				existingVcards.add(getVcardIdentification(vcard));
			} catch (IOException e) {
				
			}
		}
		contactsCursor.close();
		
		try {
			vcardsFileOutputStream = new FileOutputStream(vcardsFile, false); // don't append
		} catch (FileNotFoundException e) {
			// TODO throw error in appropriate channel
		}
		super.startDocument();
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (tagEntered) {
			vcardStringBuilder.append(ch, start, length);
		}
	}
	
	@Override
	public void startMainElement() {
		vcardStringBuilder = new StringBuilder();
	}

	@Override
	public void insert(ContentValues contentValues) {
		String vcard = vcardStringBuilder.toString().trim();
		
		String vcardIdentification = getVcardIdentification(vcard);
		
		if (!existingVcards.contains(vcardIdentification)) {
			try {
				vcardsFileOutputStream.write('\n');
				vcardsFileOutputStream.write(vcard.getBytes());
				hasEntries = true;
			} catch (IOException e) {
				addSkippedEntry();
			}
			existingVcards.add(vcardIdentification);
		} else {
			addSkippedEntry();
		}
	}
	
	@Override
	public void endDocument() throws SAXException {
		try {
			vcardsFileOutputStream.close();
			if (!isCanceled() && hasEntries) {
				Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(vcardsFile), VCARD_TYPE);
				
				context.startActivity(intent);
				// we cannot delete the temp file as the activity starts too slowly
			} else {
				vcardsFile.delete();
			}
		} catch (IOException e) {
			// TODO throw error in appropriate channel
		}
		super.endDocument();
	}
	
	private static String getVcardIdentification(String vcard) {
		String[] vcardRows = vcard.split(NEWLINE_REGEX);
		
		if (vcardRows != null && vcardRows.length > 4) {
			// the first two rows after the header contain the N and FN values
			return new StringBuilder(vcardRows[2]).append('\n').append(vcardRows[3]).toString().trim();
		} else {
			return vcard;
		}
	}
}
