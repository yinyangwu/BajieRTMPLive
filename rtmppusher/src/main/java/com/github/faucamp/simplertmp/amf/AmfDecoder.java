package com.github.faucamp.simplertmp.amf;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author francois
 */
public class AmfDecoder {

    public static AmfData readFrom(InputStream in) throws IOException {

        byte amfTypeByte = (byte) in.read();
        AmfType amfType = AmfType.valueOf(amfTypeByte);

        AmfData amfData;
        if (amfType == AmfType.NUMBER) {
            amfData = new AmfNumber();
        } else if (amfType == AmfType.BOOLEAN) {
            amfData = new AmfBoolean();
        } else if (amfType == AmfType.STRING) {
            amfData = new AmfString();
        } else if (amfType == AmfType.OBJECT) {
            amfData = new AmfObject();
        } else if (amfType == AmfType.NULL) {
            return new AmfNull();
        } else if (amfType == AmfType.UNDEFINED) {
            return new AmfUndefined();
        } else if (amfType == AmfType.MAP) {
            amfData = new AmfMap();
        } else if (amfType == AmfType.ARRAY) {
            amfData = new AmfArray();
        } else {
            throw new IOException("Unknown/unimplemented AMF data type: " + amfType);
        }

        amfData.readFrom(in);
        return amfData;
    }
}
