/*
 * ****************************************************************************
 * Copyright VMware, Inc. 2010-2016.  All Rights Reserved.
 * ****************************************************************************
 *
 * This software is made available for use under the terms of the BSD
 * 3-Clause license:
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.vmware.guest;

import com.vmware.common.annotations.Action;
import com.vmware.common.annotations.Option;
import com.vmware.common.annotations.Sample;
import com.vmware.connection.ConnectedVimServiceBase;
import com.vmware.vim25.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * DownloadGuestFile
 *
 * This sample downloads a file from the guest to a specified
 * path on the host where the client is running.
 *
 * <b>Parameters:</b>
 * url             [required] : url of the web service
 * username        [required] : username for the authentication
 * password        [required] : password for the authentication
 * vmname          [required] : name of the virtual machine
 * guestusername   [required] : username in the guest
 * guestpassword   [required] : password in the guest
 * guestfilepath   [required] : path of the file in the guest
 * localfilepath   [required] : local file path to download and store the file
 *
 * <b>Command Line:</b>
 * run.bat com.vmware.general.DownloadGuestFile --url [webserviceurl]
 * --username [username] --password [password] --vmname [vmname]
 * --guestusername [guest user] --guestpassword [guest password]
 * --guestfilepath [path of the file inside the guest]
 * --localfilepath [path to download and store the file]
 * </pre>
 */

@Sample(
        name = "download-guest-file",
        description = "This sample downloads a file from the guest to a specified\n" +
                "path on the host where the client is running. Since vSphere API 5.0"
)
public class DownloadGuestFile extends ConnectedVimServiceBase {
    @SuppressWarnings("unused")
    private X509Certificate x509CertificateToTrust;
    private ManagedObjectReference hostMOR;
    private VirtualMachinePowerState powerState;
    private String virtualMachineName;
    GuestConnection guestConnection;
    private String guestFilePath;
    private String localFilePath;

    @Option(name = "guestConnection", type = GuestConnection.class)
    public void setGuestConnection(GuestConnection guestConnection) {
        this.guestConnection = guestConnection;
    }

    @Option(name = "guestfilepath", description = "path of the file in the guest")
    public void setGuestFilePath(String path) {
        this.guestFilePath = path;
    }

    @Option(name = "localfilepath", description = "local file path to download and store the file")
    public void setLocalFilePath(String path) {
        this.localFilePath = path;
    }

    void getData(String urlString, String fileName) throws IOException {
        HttpURLConnection conn = null;
        URL urlSt = new URL(urlString);
        conn = (HttpURLConnection) urlSt.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestMethod("GET");
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(fileName);
        byte[] buf = new byte[102400];
        int len = 0;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();

        int returnErrorCode = conn.getResponseCode();
        conn.disconnect();
        if (HttpsURLConnection.HTTP_OK != returnErrorCode) {
            throw new DownloadGuestFileException("File Download is unsuccessful");
        }
    }

    @Action
    public void run() throws InvalidPropertyFaultMsg, InvalidCollectorVersionFaultMsg, TaskInProgressFaultMsg, FileFaultFaultMsg, InvalidStateFaultMsg, GuestOperationsFaultFaultMsg, CertificateException, IOException, RuntimeFaultFaultMsg {
        serviceContent.getPropertyCollector();
        virtualMachineName = guestConnection.vmname;

        Map<String, ManagedObjectReference> vms =
                getMOREFs.inFolderByType(serviceContent.getRootFolder(),
                        "VirtualMachine");
        ManagedObjectReference vmMOR = vms.get(virtualMachineName);
        if (vmMOR != null) {
            System.out.println("Virtual Machine " + virtualMachineName
                    + " found");
            powerState =
                    (VirtualMachinePowerState) getMOREFs.entityProps(vmMOR,
                            new String[]{"runtime.powerState"}).get(
                            "runtime.powerState");
            if (!powerState.equals(VirtualMachinePowerState.POWERED_ON)) {
                System.out.println("VirtualMachine: " + virtualMachineName
                        + " needs to be powered on");
                return;
            }
        } else {
            System.out.println("Virtual Machine " + virtualMachineName
                    + " not found.");
            return;
        }

        String[] opts = new String[]{"guest.guestOperationsReady"};
        String[] opt = new String[]{"guest.guestOperationsReady"};
        Object[] results =
                waitForValues.wait(vmMOR, opts, opt,
                        new Object[][]{new Object[]{true}});

        System.out.println("Guest Operations are ready for the VM");
        ManagedObjectReference guestOpManger =
                serviceContent.getGuestOperationsManager();
        ManagedObjectReference fileManagerRef =
                (ManagedObjectReference) getMOREFs.entityProps(guestOpManger,
                        new String[]{"fileManager"}).get("fileManager");

        NamePasswordAuthentication auth = new NamePasswordAuthentication();
        auth.setUsername(guestConnection.username);
        auth.setPassword(guestConnection.password);
        auth.setInteractiveSession(false);

        System.out.println("Executing DownloadFile guest operation");
        FileTransferInformation fileTransferInformation = null;
        fileTransferInformation =
                vimPort.initiateFileTransferFromGuest(fileManagerRef, vmMOR,
                        auth, guestFilePath);
        URL tempUrlObject = new URL(connection.getUrl());
        String fileDownloadUrl =
                fileTransferInformation.getUrl().replaceAll("\\*",
                        tempUrlObject.getHost());
        System.out.println("Downloading the file from :" + fileDownloadUrl
                + "");

        if (hostMOR != null) {
            opts = new String[]{"config.certificate"};
            opt = new String[]{"config.certificate"};
            results = waitForValues.wait(hostMOR, opts, opt, null);
            List<Byte> certificate = ((ArrayOfByte) results[0]).getByte();
            byte[] certificateBytes = new byte[certificate.size()];
            int index = 0;
            for (Byte b : certificate) {
                certificateBytes[index++] = b.byteValue();
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            x509CertificateToTrust =
                    (X509Certificate) cf
                            .generateCertificate(new ByteArrayInputStream(
                                    certificateBytes));
            System.out
                    .println("Certificate of the host is successfully retrieved");
        }

        getData(fileDownloadUrl, localFilePath);
        System.out.println("Successfully downloaded the file");

    }

    private class DownloadGuestFileException extends RuntimeException {
		private static final long serialVersionUID = 1L;
        public DownloadGuestFileException(String message) {
            super(message);
        }
    }
}
