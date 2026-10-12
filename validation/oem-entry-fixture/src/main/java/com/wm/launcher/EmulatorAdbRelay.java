// SPDX-License-Identifier: GPL-3.0-only
// Test-only: API 23 qemu exposes adbd via a pipe, so relay its real protocol to guest loopback.
package com.wm.launcher;

import java.io.*;
import java.net.*;
public class EmulatorAdbRelay {
    private static void pump(Socket from, Socket to) {
        try { byte[] bytes=new byte[8192]; int length; InputStream in=from.getInputStream(); OutputStream out=to.getOutputStream();
            while((length=in.read(bytes))!=-1) { out.write(bytes,0,length); out.flush(); }
        } catch(IOException ignored) { } finally { try { from.close(); to.close(); } catch(IOException ignored) { } }
    }
    public static void main(String[] args) throws Exception {
        String qemu=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.kernel.qemu");
        if(!"1".equals(qemu)) throw new IllegalStateException("Emulator only");
        ServerSocket server=new ServerSocket(); server.bind(new InetSocketAddress("127.0.0.1",5555));
        while(true) {
            Socket app=server.accept(); Socket daemon=new Socket("10.0.2.2",5595);
            new Thread(() -> pump(app,daemon)).start(); new Thread(() -> pump(daemon,app)).start();
        }
    }
}
