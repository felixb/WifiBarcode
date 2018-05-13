package de.ub0r.android.wifibarcode;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

public class XmlConfigParser {

    private static final String ns = null;
    private static final String TAG = "XmlConfigParser";

    public String parse(final InputStream in, final String ssid) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            return readNetworkList(parser, ssid);
        } finally {
            in.close();
        }
    }

    private String readNetworkList(XmlPullParser parser, final String ssid) throws XmlPullParserException, IOException {
        while (parser.getEventType() != XmlPullParser.START_TAG ||
                !parser.getName().equals("NetworkList")) {
            parser.nextTag();
        }

        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            final String password = readNetwork(parser, ssid);
            if (password != null) {
                return password;
            }
        }

        return null;
    }

    private String readNetwork(XmlPullParser parser, final String ssid) throws XmlPullParserException, IOException {
        while (parser.getEventType() != XmlPullParser.START_TAG ||
                !parser.getName().equals("WifiConfiguration")) {
            parser.nextTag();
        }

        String wifiSsid = null;
        String wifiPassword = null;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("string")) {
                final String nameAttribute = parser.getAttributeValue(ns, "name");
                if ("SSID".equals(nameAttribute)) {
                    wifiSsid = readString(parser, false);
                    if (!ssid.equals(wifiSsid)) {
                        closeTag(parser);
                        skip(parser, 3);
                        return null;
                    }
                    if (wifiPassword != null) {
                        return wifiPassword;
                    }
                } else if ("PreSharedKey".equals(nameAttribute)) {
                    wifiPassword = readString(parser, true);
                    if (wifiSsid != null) {
                        return wifiPassword;
                    }
                } else {
                    skip(parser);
                }
            } else {
                skip(parser);
            }
        }

        if (wifiSsid == null) {
            return null;
        }
        if (wifiPassword == null) {
            return "";
        }
        return wifiPassword;
    }

    private String readString(XmlPullParser parser, final boolean stripQuotes) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "string");
        String text = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "string");

        if (stripQuotes && text.matches("^\".*\"$")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }



    private void skip(final XmlPullParser parser) throws XmlPullParserException, IOException {
        skip(parser, 1);
    }

    private void skip(final XmlPullParser parser, final int startDepth) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = startDepth;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private void closeTag(final XmlPullParser parser) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            // nothing to do
        }
        parser.nextTag();
    }
}
