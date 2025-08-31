package com.kingsun.plugins.speechandtext;

import com.getcapacitor.Logger;

public class SpeechAndText {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
