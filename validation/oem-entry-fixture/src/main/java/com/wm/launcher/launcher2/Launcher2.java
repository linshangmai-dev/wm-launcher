// SPDX-License-Identifier: GPL-3.0-only
// Emulator-only reproduction of the verified card routing contract, not OEM code.
package com.wm.launcher.launcher2;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ComponentName;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.util.*;
import org.json.*;

public class Launcher2 extends Activity {
    private static byte[] field(int number, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        varint(out, number * 8 + 2); varint(out, payload.length); out.write(payload); return out.toByteArray();
    }
    private static void varint(ByteArrayOutputStream out, int n) {
        while(n >= 128) { out.write((n & 127) | 128); n >>>= 7; } out.write(n);
    }
    private static int varint(byte[] b, int[] at) {
        int n=0, shift=0, v; do { v=b[at[0]++] & 255; n|=(v&127)<<shift; shift+=7; } while(v>=128); return n;
    }
    private static List<byte[]> fields(byte[] b) {
        List<byte[]> result=new ArrayList<>(); int[] at={0};
        while(at[0]<b.length) { int tag=varint(b,at); if((tag & 7)!=2) throw new IllegalStateException();
            int length=varint(b,at); result.add(Arrays.copyOfRange(b,at[0],at[0]+length)); at[0]+=length; }
        return result;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); page.setPadding(40,100,40,40); setContentView(page);
        try {
            File file=new File(getFilesDir(),"datastore/message_card.preferences_pb");
            if(!file.exists()) {
                file.getParentFile().mkdirs();
                JSONObject card=new JSONObject().put("tipName","威课堂").put("tipType",1)
                    .put("tipTexts",new JSONArray().put("入口路由测试")).put("tipButtons",new JSONArray());
                byte[] key=field(1,"message_card_api_model".getBytes("UTF-8"));
                byte[] val=field(2,field(6,field(1,card.toString().getBytes("UTF-8"))));
                ByteArrayOutputStream entry=new ByteArrayOutputStream(); entry.write(key); entry.write(val);
                try(FileOutputStream out=new FileOutputStream(file)) { out.write(field(1,entry.toByteArray())); }
            }
            byte[] bytes;
            try(FileInputStream input=new FileInputStream(file)) { bytes=new byte[(int)file.length()]; if(input.read(bytes)!=bytes.length) throw new IOException(); }
            byte[] value=fields(fields(bytes).get(0)).get(1);
            JSONObject card=new JSONObject(new String(fields(fields(value).get(0)).get(0),"UTF-8"));
            TextView title=new TextView(this); title.setText("威课堂（入口测试替身）"); title.setTextSize(32); page.addView(title);
            JSONArray buttons=card.getJSONArray("tipButtons");
            if(buttons.length()>0) {
                JSONObject info=buttons.getJSONObject(0);
                Button button=new Button(this); button.setText(info.getString("text")); button.setTextSize(28); page.addView(button);
                button.setOnClickListener(v -> { try {
                    startActivity(new Intent().setComponent(new ComponentName(info.getString("packageName"),info.getString("url"))).addFlags(0x10200000));
                } catch(Exception e) { title.setText(e.toString()); } });
            }
        } catch(Exception e) { TextView error=new TextView(this); error.setText(e.toString()); page.addView(error); }
    }
}
