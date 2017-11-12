package org.masonapps.enablevr;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.ScrollView;
import android.widget.TextView;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import eu.chainfire.libsuperuser.Shell;

public class MainActivity extends AppCompatActivity {

    public static final String SYSTEM_ETC_PERMISSIONS = "/system/etc/permissions/";
    public static final String HANDHELD_CORE_HARDWARE_XML = "handheld_core_hardware.xml";
    public static final String ANDROID_SOFTWARE_VR_MODE_XML = "android.software.vr.mode.xml";
    public static final String ANDROID_HARDWARE_VR_HIGH_PERFORMANCE_XML = "android.hardware.vr.high_performance.xml";
    public static final String ANDROID_HARDWARE_VR_HIGH_PERFORMANCE = "android.hardware.vr.high_performance";
    public static final String ANDROID_SOFTWARE_VR_MODE = "android.software.vr.mode";
    @Nullable
    private static Shell.Interactive rootSession = null;
    private TextView textView;
    private StringBuffer stringBuffer = new StringBuffer();
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.mainTextView);
        scrollView = findViewById(R.id.scrollView);
        final PackageManager pm = getPackageManager();

        final boolean hasVrHighPerformance = pm.hasSystemFeature(ANDROID_HARDWARE_VR_HIGH_PERFORMANCE);
        Logger.d("has vr high performance: " + hasVrHighPerformance);

        final boolean hasVrSoftwareMode = pm.hasSystemFeature(ANDROID_HARDWARE_VR_HIGH_PERFORMANCE);
        Logger.d("has vr software mode: " + hasVrSoftwareMode);

        if (hasVrHighPerformance && hasVrSoftwareMode) {
            showAlreadyHaveVrDialog();
            return;
        }

        final boolean hasCompass = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS);
        final boolean hasGyro = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);
        final boolean hasAccel = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
        if (hasCompass && hasGyro && hasAccel) {
            openRootSession();
        } else {
            String message = getString(R.string.not_available);
            if (!hasCompass) {
                message += "\n\t- " + getString(R.string.compass);
            }
            if (!hasGyro) {
                message += "\n\t- " + getString(R.string.gyroscope);
            }
            if (!hasAccel) {
                message += "\n\t- " + getString(R.string.accelerometer);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.not_supported)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .create()
                    .show();
        }
    }

    private void openRootSession() {
        if (rootSession != null) {
            checkForVrFiles();
        } else {
            // Callback to report whether the shell was successfully started up 
            rootSession = new Shell.Builder()
                    .useSU()
                    .setWantSTDERR(true)
                    .setWatchdogTimeout(5)
                    .setMinimalLogging(true)
                    .open((commandCode, exitCode, output) -> {

                        if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                            final String error = "Error opening root shell: exitCode " + exitCode;
                            Logger.e(error);
                            addText("\n" + error + "\n");
                            showFatalError(R.string.no_root);
                        } else {
                            // Shell is up: send our first request 
                            checkForVrFiles();
                        }
                    });
        }
    }

    private void checkForVrFiles() {
        if (rootSession == null) return;
//      <feature name=”android.software.vr.mode” />
//      <feature name=”android.hardware.vr.high_performance” />
        final String command = "ls -1 " + SYSTEM_ETC_PERMISSIONS;
        addText("\n#" + command + "\n");
        rootSession.addCommand(command, 0, (commandCode, exitCode, output) -> {
            if (exitCode < 0) {
                logExitCodeError(exitCode);
            } else {
                handleFileListResult(output);
            }
        });
    }

    private void handleFileListResult(List<String> output) {
        boolean hasCoreHardwareFile = false;
        boolean hasVrSoftwareFile = false;
        boolean hasVrHardwareFile = false;
        for (String s : output) {
            if (s.equalsIgnoreCase(HANDHELD_CORE_HARDWARE_XML)) {
                hasCoreHardwareFile = true;
            }
            if (s.equalsIgnoreCase(ANDROID_SOFTWARE_VR_MODE_XML)) {
                hasVrSoftwareFile = true;
            }
            if (s.equalsIgnoreCase(ANDROID_HARDWARE_VR_HIGH_PERFORMANCE_XML)) {
                hasVrHardwareFile = true;
            }
        }

        if (!hasCoreHardwareFile && !hasVrHardwareFile && !hasVrSoftwareFile) {
            Logger.e(SYSTEM_ETC_PERMISSIONS + HANDHELD_CORE_HARDWARE_XML);
            showFatalError(R.string.file_missing);
        } else {
            if (hasVrHardwareFile && hasVrSoftwareFile) {
                showAlreadyHaveVrDialog();
            } else if (hasCoreHardwareFile) {
                checkCoreHardwareFile();
            } else {
                Logger.e(SYSTEM_ETC_PERMISSIONS + HANDHELD_CORE_HARDWARE_XML);
                showFatalError(R.string.file_missing);
            }
        }
    }

    private void checkCoreHardwareFile() {
        if (rootSession == null) return;
//      <feature name=”android.software.vr.mode” />
//      <feature name=”android.hardware.vr.high_performance” />
        String command = "mount -o rw,remount /system";
        addText("#" + command + "\n");
        rootSession.addCommand(command);
        command = "chmod 777 " + SYSTEM_ETC_PERMISSIONS + HANDHELD_CORE_HARDWARE_XML;
        addText("#" + command + "\n");
        rootSession.addCommand(command, 0, (commandCode, exitCode, output) -> {
            if (exitCode < 0) {
                logExitCodeError(exitCode);
            } else {
                addLines(output);
                addText("\nparsing file\n");
                parseCoreHardwareXml(new File(SYSTEM_ETC_PERMISSIONS, HANDHELD_CORE_HARDWARE_XML));
            }
        });
    }

    private void parseCoreHardwareXml(File file) {
        CompletableFuture.runAsync(() -> {
            try {
                final Document document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(file);
                if (document == null) return;
//                addText(documentToString(document) + "\n");
                final NodeList features = document.getElementsByTagName("feature");
                boolean hasVrHighPerformance = false;
                boolean hasVrSoftwareMode = false;

                List<String> featureNames = new ArrayList<>();
                for (int i = 0; i < features.getLength(); i++) {
                    final Node node = features.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        final Element element = (Element) node;
                        if (element.hasAttribute("name")) {
                            final String attribute = element.getAttribute("name");
                            featureNames.add(attribute);
                            if (attribute.equalsIgnoreCase(ANDROID_HARDWARE_VR_HIGH_PERFORMANCE))
                                hasVrHighPerformance = true;
                            if (attribute.equalsIgnoreCase(ANDROID_SOFTWARE_VR_MODE))
                                hasVrSoftwareMode = true;
                        }
                    }
                }
                addText("Available Features:\n");
                addLines(featureNames);
                if (hasVrHighPerformance && hasVrSoftwareMode) {
                    showAlreadyHaveVrDialog();
                } else {
                    fixCoreHardwareFile(document);
                    writeDocumentToFile(file, document, (commandCode, exitCode, output) -> {
                        if (exitCode < 0) {
                            logExitCodeError(exitCode);
                        } else {
                            addLines(output);
                            fixPermissionsAndFinish();
                        }
                    });
                }
            } catch (SAXException | IOException | ParserConfigurationException | TransformerException e) {
                Logger.e("failed to parse xml", e);
                showFatalError(e.getLocalizedMessage());
            }
        });
    }

    private void logExitCodeError(int exitCode) {
        final String error = "Error adding command: exitCode " + exitCode;
        Logger.e(error);
        addText("\n" + error + "\n");
    }

    private void writeDocumentToFile(File file, Document document, Shell.OnCommandResultListener onCommandResultListener) throws IOException, TransformerException {
//        addText("\n****new file****\n");
//        addText(documentToString(document) + "\n");
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        final DOMSource source = new DOMSource(document);
        final File tempFile = File.createTempFile("handheld_core", "xml");
        final Result target = new StreamResult(tempFile);
        transformer.transform(source, target);
        if (rootSession != null) {
            final String command = "cat " + tempFile.getAbsolutePath() + " > " + file.getAbsolutePath();
            addText("\n#" + command + "\n");
            rootSession.addCommand(command, 0, onCommandResultListener);
        } else
            showFatalError("error su session ended");
    }

    private String documentToString(Document document) throws TransformerException {
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        final DOMSource source = new DOMSource(document);
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        final Result target = new StreamResult(stream);
        transformer.transform(source, target);
        return stream.toString();
    }

    @Nullable
    private Document parseXml(String text) {
        try {
            return DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(text)));

        } catch (SAXException | IOException | ParserConfigurationException e) {
            Logger.e("failed to parse xml", e);
            showFatalError(e.getLocalizedMessage());
        }
        return null;
    }

    private void fixCoreHardwareFile(Document document) {
        addText("\nfixing file\n");
        try {
            final Node root = document.getElementsByTagName("permissions").item(0);

            final Element featureHW = document.createElement("feature");
            root.appendChild(featureHW);
            final Attr nameHW = document.createAttribute("name");
            nameHW.setValue(ANDROID_HARDWARE_VR_HIGH_PERFORMANCE);
            featureHW.setAttributeNode(nameHW);

            final Element featureSW = document.createElement("feature");
            root.appendChild(featureSW);
            final Attr nameSW = document.createAttribute("name");
            nameSW.setValue(ANDROID_SOFTWARE_VR_MODE);
            featureSW.setAttributeNode(nameSW);
            addText("\nfixing file complete\n");
        } catch (Exception e) {
            Logger.e("failed to add lines to xml", e);
            addText("\nfixing file failed\n");
            showFatalError(e.getLocalizedMessage());
        }
    }

    private void fixPermissionsAndFinish() {
        if (rootSession == null) return;
        final String command = "chmod 744 " + SYSTEM_ETC_PERMISSIONS + HANDHELD_CORE_HARDWARE_XML;
        addText("\n#" + command + "\n");
        rootSession.addCommand(command, 0, (commandCode, exitCode, output) -> {
            if (exitCode < 0) {
                logExitCodeError(exitCode);
            } else {
                addLines(output);
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.vr_enabled)
                        .setMessage(R.string.vr_message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                        .create()
                        .show());
            }
        });
    }

    private void showFatalError(int messageId) {
        showFatalError(getString(messageId));
    }

    private void showFatalError(String message) {
        Logger.e(message);
        addText("\n" + message + "\n");
        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .create()
                .show());
    }

    private void showAlreadyHaveVrDialog() {
        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.vr_already_enabled)
                .setMessage(R.string.vr_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .create()
                .show());
    }

    private String combineLines(List<String> list) {
        final StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString();
    }

    private void addLines(List<String> lines) {
        for (String s : lines) {
            addText(s);
            addText("\n");
        }
        scrollToBottom();
    }

    private void addText(String s) {
        stringBuffer.append(s);
        scrollToBottom();
    }

    private void scrollToBottom() {
        runOnUiThread(() -> {
            textView.setText(stringBuffer);
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

//    Enables Daydream VR by adding features to the handheld_core_hardware.xml
//    android.software.vr.mode
//    android.hardware.vr.high_performance
//
//    DEVICE REQUIREMENTS
//    ROOT ACCESS
//    ANDROID 7.0+
//    ACCELEROMETER, GYROSCOPE, AND COMPASS
//
//    doesn't install VR Services or Daydream Home and Keyboard
}
