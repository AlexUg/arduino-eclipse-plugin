package io.sloeber.core.txt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import io.sloeber.core.Activator;
import io.sloeber.core.common.Common;
import io.sloeber.core.common.Const;
import io.sloeber.core.managers.ArduinoPlatform;
import io.sloeber.core.tools.FileModifiers;
import io.sloeber.core.tools.Version;

/**
 * A class to apply workarounds to installed packages. Workaround are done after
 * installation and at usage of boards.txt or platform.txt file currently there
 * are noworkarounds for programmers.trx
 * 
 * The first line of the worked around files contain a key. A newer version of
 * sloeber that has a different workaround should change the key. This way the
 * worked around files can be persisted and updated when needed
 * 
 * @author jan
 *
 */
@SuppressWarnings("nls")
public class WorkAround extends Const {
    // Each time this class is touched consider changing the String below to enforce
    // updates
    // for debugging I added the system time so the files get refresed at each run
    private static final String FIRST_SLOEBER_WORKAROUND_LINE = "#Sloeber created workaound file V1.01.test 1 "
            + String.valueOf(System.currentTimeMillis());

    /**
     * workarounds done at installation time. I try to keep those at a minimum but
     * none platform.txt and boards.txt workarounds need to be done during install
     * time
     * 
     * @param platform
     */
    static public void applyKnownWorkArounds(ArduinoPlatform platform) {

        /*
         * for STM32 V1.8 and later #include "SrcWrapper.h" to Arduino.h remove the
         * prebuild actions remove the build_opt
         * https://github.com/Sloeber/arduino-eclipse-plugin/issues/1143
         */
        if (Version.compare("1.8.0", platform.getVersion()) != 1) {
            if ("stm32".equals(platform.getArchitecture())) {
                if ("STM32".equals(platform.getParent().getName())) {
                    if (Version.compare("1.8.0", platform.getVersion()) == 0) {
                        File arduino_h = platform.getInstallPath().append("cores").append("arduino").append("Arduino.h")
                                .toFile();
                        if (arduino_h.exists()) {
                            FileModifiers.replaceInFile(arduino_h, false, "#include \"pins_arduino.h\"",
                                    "#include \"pins_arduino.h\"\n#include \"SrcWrapper.h\"");
                        }
                    }
                }
            }
        }

        MakePlatformSloeberTXT(platform.getPlatformFile());
        MakeBoardsSloeberTxt(platform.getBoardsFile());

    }

    /**
     * Get the a workaround boards.txt and if needed create/update it This method
     * takes a boards.txt file and returns a worked around file. The worked around
     * file is persisted on disk for easy debugging/ reduce code impact and
     * performance.
     * 
     * @param requestedFileToWorkAround
     *            the board.txt that you want to process
     * 
     * @return the worked around file or requestedFileToWorkAround if it does not
     *         exist or an error occurred
     */
    static public File MakeBoardsSloeberTxt(File requestedFileToWorkAround) {
        if (!requestedFileToWorkAround.exists()) {
            return requestedFileToWorkAround;
        }
        String inFile = requestedFileToWorkAround.toString();
        String actualFileToLoad = inFile.replace(BOARDS_FILE_NAME, "boards.sloeber.txt");
        if (inFile.equals(actualFileToLoad)) {
            Common.log(new Status(IStatus.ERROR, Activator.getId(),
                    "Boards.txt file is not recognized " + requestedFileToWorkAround.toString()));
            return requestedFileToWorkAround;
        }
        File boardsSloeberTXT = new File(actualFileToLoad);
        if (boardsSloeberTXT.exists()) {
            // delete if outdated
            String firstLine = null;
            try (BufferedReader Buff = new BufferedReader(new FileReader(boardsSloeberTXT));) {
                firstLine = Buff.readLine();
            } catch (Exception e) {
                // ignore and delete the file
            }
            if (!FIRST_SLOEBER_WORKAROUND_LINE.equals(firstLine.trim())) {
                boardsSloeberTXT.delete();
            }
        }
        if (!boardsSloeberTXT.exists()) {
            try {
                String boardsTXT = FIRST_SLOEBER_WORKAROUND_LINE + "\n";
                boardsTXT += FileUtils.readFileToString(requestedFileToWorkAround, Charset.defaultCharset());

                boardsTXT = boardsTXT.replace("\r\n", "\n");
                // because I search for spaces around string as delimiters I add a space at the
                // end of the line
                boardsTXT = boardsTXT.replace("\n", " \n");
                boardsTXT = solveOSStuff(boardsTXT);

                String correctMAN = " \"-DUSB_MANUFACTURER=\\\"{build.usb_manufacturer}\\\"\" ";
                String correctPROD = " \"-DUSB_PRODUCT=\\\"{build.usb_product}\\\"\" ";
                String correctBOARD = " \"-DARDUINO_BOARD=\\\"{build.board}\\\"\" ";

                if (SystemUtils.IS_OS_WINDOWS) {
                    // replace FI circuitplay32u4cat.build.usb_manufacturer="Adafruit"
                    // with circuitplay32u4cat.build.usb_manufacturer=Adafruit
                    boardsTXT = boardsTXT.replaceAll("(\\S+\\.build\\.usb\\S+)=\\\"(.+)\\\"", "$1=$2");

                    // quoting fixes for embedutils
                    // ['\"]?(-DMBEDTLS_\S+)=\\?"(mbedtls\S+?)\\?\"["']? \"$1=\\\"$2\\\"\"
                    boardsTXT = boardsTXT.replaceAll(" ['\\\"]?(-DMBEDTLS_\\S+)=\\\\?\"(mbedtls\\S+?)\\\\?\\\"[\"']? ",
                            " \\\"$1=\\\\\\\"$2\\\\\\\"\\\" ");

                } else {
                    correctMAN = " '-DUSB_MANUFACTURER=\"{build.usb_manufacturer}\"' ";
                    correctPROD = " '-DUSB_PRODUCT=\"{build.usb_product}\" '";
                    correctBOARD = " '-DARDUINO_BOARD=\"{build.board}\"' ";
                }
                // some providers put -DUSB_PRODUCT={build.usb_product} in boards.txt
                boardsTXT = boardsTXT.replace(" \"-DUSB_MANUFACTURER={build.usb_manufacturer}\" ", correctMAN);
                boardsTXT = boardsTXT.replace(" \"-DUSB_PRODUCT={build.usb_product}\" ", correctPROD);
                boardsTXT = boardsTXT.replace(" -DARDUINO_BOARD=\"{build.board}\" ", correctBOARD);

                boardsTXT = boardsTXT.replace(" '-DUSB_MANUFACTURER={build.usb_manufacturer}' ", correctMAN);
                boardsTXT = boardsTXT.replace(" '-DUSB_PRODUCT={build.usb_product}' ", correctPROD);
                boardsTXT = boardsTXT.replace(" '-DARDUINO_BOARD=\"{build.board}' ", correctBOARD);
                boardsTXT = boardsTXT.replace("{", "${" + ERASE_START);
                FileUtils.write(boardsSloeberTXT, boardsTXT, Charset.defaultCharset());
            } catch (IOException e) {
                Common.log(new Status(IStatus.WARNING, Activator.getId(),
                        "Failed to apply work arounds to " + requestedFileToWorkAround.toString(), e));
                return requestedFileToWorkAround;
            }
        }
        return boardsSloeberTXT;
    }

    /**
     * * Get the a workaround platform.txt and if needed create/update it This
     * method takes a platform.txt file and returns a worked around file. The worked
     * around file is persisted on disk for easy debugging/ reduce code impact and
     * performance.
     * 
     * 
     * @param requestedFileToWorkAround
     *            the platform.txt you want to process
     * 
     * @return the worked around file or requestedFileToWorkAround if it does not
     *         exist or an error occurred
     */
    public static File MakePlatformSloeberTXT(File requestedFileToWorkAround) {

        // zoek voor
        // recipe.objcopy.*.pattern=cmd /c *
        // verwijder cmd /c

        if (!requestedFileToWorkAround.exists()) {
            return requestedFileToWorkAround;
        }
        String inFile = requestedFileToWorkAround.toString();
        String actualFileToLoad = inFile.replace(PLATFORM_FILE_NAME, "platform.sloeber.txt");
        if (inFile.equals(actualFileToLoad)) {
            Common.log(new Status(IStatus.ERROR, Activator.getId(),
                    "platform.txt file is not recognized " + requestedFileToWorkAround.toString()));
            return requestedFileToWorkAround;
        }
        File platformSloeberTXT = new File(actualFileToLoad);
        if (platformSloeberTXT.exists()) {
            // delete if outdated
            String firstLine = null;
            try (BufferedReader Buff = new BufferedReader(new FileReader(platformSloeberTXT));) {
                firstLine = Buff.readLine();
            } catch (Exception e) {
                // ignore and delete the file
            }
            if (!FIRST_SLOEBER_WORKAROUND_LINE.equals(firstLine.trim())) {
                platformSloeberTXT.delete();
            }
        }
        if (!platformSloeberTXT.exists()) {
            try {
                String platformTXT = FIRST_SLOEBER_WORKAROUND_LINE + "\n";
                platformTXT += FileUtils.readFileToString(requestedFileToWorkAround, Charset.defaultCharset());
                platformTXT = platformTXT.replace("\r\n", "\n");

                platformTXT = solveOSStuff(platformTXT);

                // Arduino treats core differently so we need to change the location of directly
                // referenced files this manifests only in the combine recipe
                String inCombineRecipe = findLineStartingWith(platformTXT, "recipe.c.combine.pattern");
                if (null != inCombineRecipe) {
                    String outCombineRecipe = inCombineRecipe.replaceAll("(\\{build\\.path})(/core)?/sys",
                            "$1/core/core/sys");
                    platformTXT = platformTXT.replace(inCombineRecipe, outCombineRecipe);
                }

                // change {cmd.path} to fqn {cmd.path}
                String searchString = "{cmd.path}";
                String cmdPathLine = findLineContaining(platformTXT, searchString);
                while (null != cmdPathLine) {
                    if (cmdPathLine.startsWith(TOOLS)) {
                        int patternIndex = cmdPathLine.indexOf(PATTERN);
                        if (patternIndex > 0) {
                            int endIndex = cmdPathLine.lastIndexOf(DOT, patternIndex - 2);
                            String replaceString = "{" + cmdPathLine.substring(0, endIndex) + "cmd.path}";
                            String replaceLine = cmdPathLine.replace(searchString, replaceString);
                            platformTXT = platformTXT.replace(cmdPathLine, replaceLine);
                            cmdPathLine = findLineContaining(platformTXT, searchString);
                        }
                    }
                }

                // change {config.path} to fqn {config.path}
                searchString = "{config.path}";
                cmdPathLine = findLineContaining(platformTXT, searchString);
                while (null != cmdPathLine) {
                    if (cmdPathLine.startsWith(TOOLS)) {
                        int patternIndex = cmdPathLine.indexOf(PATTERN);
                        if (patternIndex > 0) {
                            int endIndex = cmdPathLine.lastIndexOf(DOT, patternIndex - 2);
                            String replaceString = "{" + cmdPathLine.substring(0, endIndex) + "config.path}";
                            String replaceLine = cmdPathLine.replace(searchString, replaceString);
                            platformTXT = platformTXT.replace(cmdPathLine, replaceLine);
                            cmdPathLine = findLineContaining(platformTXT, searchString);
                        }
                    }
                }

                // workaround for infineon arm v1.4.0 overwriting the default to a wrong value
                platformTXT = platformTXT.replace("\nbuild.core.path", "\n#line removed by Sloeber build.core.path");

                try { // https://github.com/Sloeber/arduino-eclipse-plugin/issues/1182#
                    Path platformTXTPath = new Path(requestedFileToWorkAround.toString());
                    int totalSegments = platformTXTPath.segmentCount();
                    String platformVersion = platformTXTPath.segment(totalSegments - 2);
                    String platformArchitecture = platformTXTPath.segment(totalSegments - 3);
                    String platformName = platformTXTPath.segment(totalSegments - 5);
                    if (Version.compare("1.8.0", platformVersion) != 1) {
                        if ("stm32".equals(platformArchitecture)) {
                            if ("STM32".equals(platformName)) {
                                platformTXT = platformTXT.replace("\"@{build.opt.path}\"", "");
                                platformTXT = platformTXT.replaceAll("recipe\\.hooks\\.prebuild\\..*", "");
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }

                // for adafruit nfr
                platformTXT = platformTXT.replace(" -DARDUINO_BSP_VERSION=\"{version}\" ",
                        " \"-DARDUINO_BSP_VERSION=\\\"{version}\\\"\" ");
                platformTXT = platformTXT.replace(" '-DARDUINO_BSP_VERSION=\"{version}\"' ",
                        " \"-DARDUINO_BSP_VERSION=\\\"{version}\\\"\" ");

                if (SystemUtils.IS_OS_WINDOWS) {
                    // replace FI '-DUSB_PRODUCT={build.usb_product}' with
                    // "-DUSB_PRODUCT=\"{build.usb_product}\""
                    platformTXT = platformTXT.replaceAll("\\'-D(\\S+)=\\{(\\S+)}\\'", "\"-D$1=\\\\\"{$2}\\\\\"\"");

                    // quoting fixes for embedutils
                    platformTXT = platformTXT.replaceAll("\"?(-DMBEDTLS_\\S+)=\\\\?\"(mbedtls\\S+)\"\\\\?\"*",
                            "\"$1=\\\\\"$2\\\\\"\"");

                    // Sometimes "-DUSB_MANUFACTURER={build.usb_manufacturer}"
                    // "-DUSB_PRODUCT={build.usb_product}"
                    // is used fi LinKit smart
                    platformTXT = platformTXT.replace("\"-DUSB_MANUFACTURER={build.usb_manufacturer}\"",
                            "\"-DUSB_MANUFACTURER=\\\"{build.usb_manufacturer}\\\"\"");
                    platformTXT = platformTXT.replace("\"-DUSB_PRODUCT={build.usb_product}\"",
                            "\"-DUSB_PRODUCT=\\\"{build.usb_product}\\\"\"");
                    platformTXT = platformTXT.replace(" -DARDUINO_BOARD=\"{build.board}\" ",
                            " \"-DARDUINO_BOARD=\\\"{build.board}\\\"\" ");

                } else {
                    platformTXT = platformTXT.replace(" -DUSB_MANUFACTURER={build.usb_manufacturer} ",
                            " '-DUSB_MANUFACTURER=\"{build.usb_manufacturer}\"' ");
                    platformTXT = platformTXT.replace(" -DUSB_PRODUCT={build.usb_product} ",
                            " '-DUSB_PRODUCT=\"{build.usb_product}\" '");
                    platformTXT = platformTXT.replace(" -DARDUINO_BOARD=\"{build.board}\" ",
                            " '-DARDUINO_BOARD=\"{build.board}\"' ");
                }
                platformTXT = platformTXT.replace("{", "${" + ERASE_START);
                FileUtils.write(platformSloeberTXT, platformTXT, Charset.defaultCharset());
            } catch (IOException e) {
                Common.log(new Status(IStatus.WARNING, Activator.getId(),
                        "Failed to apply work arounds to " + requestedFileToWorkAround.toString(), e));
                return requestedFileToWorkAround;
            }
        }
        return platformSloeberTXT;
    }

    private static String findLineStartingWith(String text, String startOfLine) {
        int lineStartIndex = text.indexOf("\n" + startOfLine) + 1;
        if (lineStartIndex > 0) {
            int lineEndIndex = text.indexOf("\n", lineStartIndex) - 1;
            if (lineEndIndex > 0) {
                return text.substring(lineStartIndex, lineEndIndex);
            }
        }
        return null;
    }

    private static String findLineContaining(String text, String searchString) {
        int SearchStringIndex = text.indexOf(searchString) + 1;
        int lineStartIndex = text.lastIndexOf("\n", SearchStringIndex) + 1;
        if (lineStartIndex > 0) {
            int lineEndIndex = text.indexOf("\n", lineStartIndex) - 1;
            if (lineEndIndex > 0) {
                return text.substring(lineStartIndex, lineEndIndex);
            }
        }
        return null;
    }


    private static String solveOSStuff(String inpuText) {
        String WINDOWSKEY = ".windows"; //$NON-NLS-1$
        String LINUXKEY = ".linux"; //$NON-NLS-1$
        String MACKEY = ".macosx"; //$NON-NLS-1$
        LinkedList<String> Otherosses = new LinkedList<>();

        String thisOSKey = null;
        switch (Platform.getOS()) {
        case Platform.OS_WIN32: {
            thisOSKey = WINDOWSKEY;
            Otherosses.add(LINUXKEY);
            Otherosses.add(MACKEY);
            break;
        }
        case Platform.OS_LINUX: {
            thisOSKey = LINUXKEY;
            Otherosses.add(WINDOWSKEY);
            Otherosses.add(MACKEY);
            break;
        }
        case Platform.OS_MACOSX: {
            thisOSKey = MACKEY;
            Otherosses.add(WINDOWSKEY);
            Otherosses.add(LINUXKEY);
            break;
        }
        default:
            Common.log(new Status(IStatus.ERROR, Activator.getId(), "Failed to recognize the os you are using"));
            return inpuText;
        }
        ;

        String ret = inpuText;
        String searchKey = null;
        // remove the os keys
        for (String curos : Otherosses) {
            searchKey = DOT + curos + EQUAL;
            String notNeededosLine = findLineContaining(ret, searchKey);
            while (!notNeededosLine.isEmpty()) {
                ret = ret.replace(notNeededosLine, EMPTY);
                notNeededosLine = findLineContaining(ret, searchKey);
            }
        }
        searchKey = DOT + thisOSKey + EQUAL;
        String neededOSLine = findLineContaining(ret, searchKey);
        while (null != neededOSLine) {
            int keyIndex = neededOSLine.indexOf(searchKey);
            String genericKey = neededOSLine.substring(0, keyIndex) + EQUAL;
            String matchingGenericLine = findLineContaining(ret, genericKey);
            if (null != matchingGenericLine) {
                ret = ret.replace(matchingGenericLine, EMPTY);
            }
            ret = ret.replace(neededOSLine, EMPTY);
            neededOSLine = findLineContaining(ret, searchKey);
        }
        ret = ret.replace(searchKey, EQUAL);
        return ret;
    }
}