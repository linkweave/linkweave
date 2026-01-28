/*
 *
 * Copyright (C) 2025 DV Bern AG, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.dvbern.dvbstarter.openapi;

import lombok.experimental.UtilityClass;

@UtilityClass
@SuppressWarnings("EmptyClass")
public class OpenApiConst {
    @UtilityClass
    public static final class Tags {
        public static final String CLIENT_CONFIG = "Client-Config";
        public static final String EXAMPLE_LOGGING = "Example-Logging";
        public static final String TAG_AERZTE = "Aerzte";
        public static final String TAG_AKTIVITAET = "Aktivitaet";
        public static final String TAG_ANLASS = "Anlass";
        public static final String TAG_ANLASSCONFIG = "Anlass-Config";
        public static final String TAG_AUFGABE = "Aufgabe";
        public static final String TAG_BENUTZER = "Benutzer";
        public static final String TAG_BRIEFVORLAGE = "Briefvorlage";
        public static final String TAG_DEVELOPER = "Developer";
        public static final String TAG_DOCUMENT_QUEUE = "Document-Queue";
        public static final String TAG_DOSSIER = "Dossier";
        public static final String TAG_DUPLIKATE = "Duplikate";
        public static final String TAG_EINLADUNGEN = "Einladungen";
        public static final String TAG_ESCADA = "Escada";
        public static final String TAG_FILE_INGRESS = "FileIngress";
        public static final String TAG_IMPFSTOFF_CHARGEN = "Impfstoff-Chargen";
        public static final String TAG_IMPFSTOFFE = "Impfstoffe";
        public static final String TAG_INFOMATERIAL = "Infomaterial";
        public static final String TAG_KINDER = "Kinder";
        public static final String TAG_KINDANLAESSE = "Kindanlaesse";
        public static final String TAG_KLASSEN = "Klassen";
        public static final String TAG_KONTAKTPERSON_KIND = "Kontaktperson-Kind";
        public static final String TAG_KONTAKTPERSON_SCHULE = "Kontaktperson-Schule";
        public static final String TAG_LABELS = "Labels";
        public static final String TAG_LEHRPERSON = "Lehrperson";
        public static final String TAG_MASSENIMPORT = "Massenimport";
        public static final String TAG_NOTIZEN = "Notizen";
        public static final String TAG_PLZ = "Plz";
        public static final String TAG_PRINTER = "Printer";
        public static final String TAG_SCHULEN = "Schulen";
        public static final String TAG_SEARCH = "Search";
        public static final String TAG_SETTINGS = "Tenant-Settings";
        public static final String TAG_SUPER_ADMIN_RUNNER = "Super-Admin-Runner";
        public static final String TAG_TIMETRAVEL = "Timetravel";
        public static final String TAG_QUARTIERE = "Quartiere";
        public static final String TAG_UNTERSUCHUNG_ARZT = "UntersuchungArzt";
        public static final String TAG_UNTERSUCHUNG_DURCHFUEHRUNG = "UntersuchungDurchfuehrung";
        public static final String TAG_UNTERSUCHUNG_GROESSEGEWICHT = "UntersuchungGroesseGewicht";
        public static final String TAG_UNTERSUCHUNG_HOEREN_AUDIOGRAMM = "UntersuchungHoerenAudiogramm";
        public static final String TAG_UNTERSUCHUNG_HOEREN_OTOSKOPIE = "UntersuchungHoerenOtoskopie";
        public static final String TAG_UNTERSUCHUNG_IMPFUNGEN = "UntersuchungImpfungen";
        public static final String TAG_UNTERSUCHUNG_KIND_DATEN = "UntersuchungKindDaten";
        public static final String TAG_UNTERSUCHUNG_MASSNAHMEN = "UntersuchungMassnahmen";
        public static final String TAG_UNTERSUCHUNG_SEHEN = "UntersuchungSehen";
        public static final String TAG_UNTERSUCHUNG_TRIAGE = "UntersuchungTriage";
        public static final String TAG_UNTERSUCHUNG_LAEUSEKONTROLLE = "UntersuchungLaeusekontrolle";
    }

    @UtilityClass
    public static final class Format {
        public static final String BINARY = "binary";
        public static final String EDOKIDID = "edokid-id";
        public static final String ENTITY_ID = "entity-id";
        public static final String EXCEPTION_ID = "exception-id";
        public static final String DATE = "date";
        public static final String DATE_TIME = "date-time";
        public static final String EMAIL = "email";
        public static final String PHONE_NUMBER = "phone-number";
        public static final String NUMBER_PRECISE = "number-precise";
        public static final String NUMBER_ZAHL = "number-zahl";
        public static final String SEMANTIC_VERSION = "semantic-version";
        public static final String SOZIALVERSICHERUNGSNUMMER = "sozialversicherungsnummer";
        public static final String URL = "url";
        public static final String UUID = "uuid";
    }

}
