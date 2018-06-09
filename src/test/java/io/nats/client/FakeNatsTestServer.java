// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the begining of the connect sequence, all hard coded, but
 * is configurable to fail at specific points to allow client testing.
 */
public class FakeNatsTestServer implements Closeable{

    // Default is to exit after pong
    public enum ExitAt {
        EXIT_BEFORE_INFO,
        EXIT_AFTER_INFO,
        EXIT_AFTER_CONNECT,
        EXIT_AFTER_PING,
        NO_EXIT
    }

    public enum Progress {
        NO_CLIENT,
        CLIENT_CONNECTED,
        SENT_INFO,
        GOT_CONNECT,
        GOT_PING,
        SENT_PONG,
        STARTED_CUSTOM_CODE,
        COMPLETED_CUSTOM_CODE,
    }

    public interface Customizer {
        public void customizeTest(FakeNatsTestServer ts, BufferedReader reader, PrintWriter writer);
    }

    private int port;
    private ExitAt exitAt;
    private Progress progress;
    private boolean protocolFailure;
    private CompletableFuture<Boolean> waitForIt;
    private Customizer customizer;

    public FakeNatsTestServer(ExitAt exitAt) {
        this(NatsTestServer.nextPort(), exitAt);
    }

    public FakeNatsTestServer(int port, ExitAt exitAt) {
        this.port = port;
        this.exitAt = exitAt;
        start();
    }

    public FakeNatsTestServer(Customizer custom) {
        this.port = NatsTestServer.nextPort();
        this.exitAt = ExitAt.NO_EXIT;
        this.customizer = custom;
        start();
    }

    private void start() {
        this.progress = Progress.NO_CLIENT;
        this.waitForIt = new CompletableFuture<>();
        Thread t = new Thread(() -> {accept();});
        t.start();
    }

    public int getPort() {
        return port;
    }

    public Progress getProgress() {
        return this.progress;
    }

    // True if the failure was not intentional
    public boolean wasProtocolFailure() {
        return protocolFailure;
    }

    public void close() {
        waitForIt.complete(Boolean.TRUE);
    }

    public void accept() {
        ServerSocket serverSocket = null;
        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;

        try {
            serverSocket = new ServerSocket(this.port);
            serverSocket.setSoTimeout(5000);

            System.out.println("*** Fake Server @" + this.port + " started...");
            socket = serverSocket.accept();
            
            this.progress = Progress.CLIENT_CONNECTED;
            System.out.println("*** Fake Server @" + this.port + " got client...");

            writer = new PrintWriter(socket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            if (exitAt == ExitAt.EXIT_BEFORE_INFO) {
                throw new Exception("exit");
            }

            writer.write("INFO {\"server_id\":\"test\"}\r\n");
            writer.flush();
            this.progress = Progress.SENT_INFO;
            System.out.println("*** Fake Server @" + this.port + " sent info...");

            if (exitAt == ExitAt.EXIT_AFTER_INFO) {
                throw new Exception("exit");
            }

            String connect = reader.readLine();

            if (connect.startsWith("CONNECT")) {
                this.progress = Progress.GOT_CONNECT;
                System.out.println("*** Fake Server @" + this.port + " got connect...");
            } else {
                throw new IOException("First message wasn't CONNECT");
            }

            if (exitAt == ExitAt.EXIT_AFTER_CONNECT) {
                throw new Exception("exit");
            }

            String ping = reader.readLine();

            if (ping.startsWith("PING")) {
                this.progress = Progress.GOT_PING;
                System.out.println("*** Fake Server @" + this.port + " got ping...");
            } else {
                throw new IOException("Second message wasn't PING");
            }

            if (exitAt == ExitAt.EXIT_AFTER_PING) {
                throw new Exception("exit");
            }

            writer.write("PONG\r\n");
            writer.flush();
            this.progress = Progress.SENT_PONG;
            System.out.println("*** Fake Server @" + this.port + " sent pong...");

            if (this.customizer != null) {
                this.progress = Progress.STARTED_CUSTOM_CODE;
                System.out.println("*** Fake Server @" + this.port + " starting custom code...");
                this.customizer.customizeTest(this, reader, writer);
                this.progress = Progress.COMPLETED_CUSTOM_CODE;
            }
            waitForIt.get(); // Wait for the test to cancel us

        } catch (IOException io) {
            protocolFailure = true;
            System.out.println("\n*** Fake Server @" + this.port + " got exception "+io.getMessage());
        } catch (Exception ex) {
            System.out.println("\n*** Fake Server @" + this.port + " got exception "+ex.getMessage());
        }
        finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ex) {
                    System.out.println("\n*** Fake Server @" + this.port + " got exception "+ex.getMessage());
                }
            }
            if (socket != null) {
                try {
                    writer.close();
                    reader.close();
                    socket.close();
                } catch (IOException ex) {
                    System.out.println("\n*** Fake Server @" + this.port + " got exception "+ex.getMessage());
                }
            }
        }
        System.out.println("*** Fake Server @" + this.port + " completed...");
    }
}