package tools.mapletools;

import provider.wz.WZFiles;
import tools.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author RonanLana
 * <p>
 * The main objective of this program is to index relevant reagent data
 * from the Item.wz folder and generate a SQL table with them, to be used
 * by the server source.
 */
public class SkillMakerReagentIndexer {
    private static final Path INPUT_FILE = WZFiles.ITEM.getFile().resolve("Etc/0425.img.xml");
    private static final Path OUTPUT_FILE = ToolConstants.getOutputFile("maker-reagent-data.sql");
    private static final int INITIAL_STRING_LENGTH = 50;
    private static final List<Pair<Integer, Pair<String, Integer>>> reagentList = new ArrayList<>();

    private static PrintWriter printWriter = null;
    private static BufferedReader bufferedReader = null;
    private static byte status = 0;
    private static int id = -1;

    private static String getName(String token) {
        int i, j;
        char[] dest;
        String d;

        i = token.lastIndexOf("name");
        i = token.indexOf("\"", i) + 1; //lower bound of the string
        j = token.indexOf("\"", i);     //upper bound

        dest = new char[INITIAL_STRING_LENGTH];
        token.getChars(i, j, dest, 0);

        d = new String(dest);
        return (d.trim());
    }

    private static String getValue(String token) {
        int i, j;
        char[] dest;
        String d;

        i = token.lastIndexOf("value");
        i = token.indexOf("\"", i) + 1; //lower bound of the string
        j = token.indexOf("\"", i);     //upper bound

        dest = new char[INITIAL_STRING_LENGTH];
        token.getChars(i, j, dest, 0);

        d = new String(dest);
        return (d.trim());
    }

    private static void simpleToken(String token) {
        if (token.contains("/imgdir")) {
            status -= 1;
        } else if (token.contains("imgdir")) {
            status += 1;
        }
    }

    private static void forwardCursor(int st) {
        String line = null;

        try {
            while (status >= st && (line = bufferedReader.readLine()) != null) {
                simpleToken(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void translateToken(String token) {
        String d;

        if (token.contains("/imgdir")) {
            status -= 1;
        } else if (token.contains("imgdir")) {
            if (status == 1) {           //getting id
                d = getName(token);
                id = Integer.parseInt(d);
                System.out.println("Parsing maker reagent id " + id);
            } else if (status == 2) {
                d = getName(token);
                if (!d.equals("info")) {
                    System.out.println("not info");
                    forwardCursor(status);
                }
            }

            status += 1;
        } else {
            if (status == 3) {
                if (token.contains("int")) {
                    d = getName(token);

                    if (d.contains("inc") || d.contains("rand")) {
                        Integer v = Integer.valueOf(getValue(token));
                        Pair<String, Integer> reagBuff = new Pair<>(d, v);

                        Pair<Integer, Pair<String, Integer>> reagItem = new Pair<>(id, reagBuff);
                        reagentList.add(reagItem);
                    }
                } else {
                    if (token.contains("canvas")) {
                        forwardCursor(status + 1);
                    }
                }
            }
        }
    }

    private static void SortReagentList() {
        reagentList.sort((p1, p2) -> p1.getLeft().compareTo(p2.getLeft()));
    }

    private static void WriteMakerReagentTableFile() {
        printWriter.println(" # SQL File autogenerated from the MapleSkillMakerReagentIndexer feature by Ronan Lana.");
        printWriter.println(" # Generated data is conformant with the Item.wz folder used to compile this.");
        printWriter.println();

        printWriter.println("CREATE TABLE IF NOT EXISTS `makerreagentdata` (");
        printWriter.println("  `itemid` int(11) NOT NULL,");
        printWriter.println("  `stat` varchar(20) NOT NULL,");
        printWriter.println("  `value` smallint(6) NOT NULL,");
        printWriter.println("  PRIMARY KEY (`itemid`)");
        printWriter.println(");");
        printWriter.println();

        StringBuilder sb = new StringBuilder("INSERT IGNORE INTO `makerreagentdata` (`itemid`, `stat`, `value`) VALUES\r\n");

        for (Pair<Integer, Pair<String, Integer>> it : reagentList) {
            sb.append("  (" + it.left + ", \"" + it.right.left + "\", " + it.right.right + "),\r\n");
        }

        sb.setLength(sb.length() - 3);
        sb.append(";");

        printWriter.println(sb);
    }

    private static void writeMakerReagentTableData() {
        // This will reference one line at a time
        String line = null;

        try(PrintWriter pw = new PrintWriter(Files.newOutputStream(OUTPUT_FILE));
            	BufferedReader br = Files.newBufferedReader(INPUT_FILE);) {
            bufferedReader = br;

            while ((line = bufferedReader.readLine()) != null) {
                translateToken(line);
            }


            SortReagentList();

            printWriter = pw;
            WriteMakerReagentTableFile();
        } catch (FileNotFoundException ex) {
            System.out.println("Unable to open file '" + OUTPUT_FILE + "'");
        } catch (IOException ex) {
            System.out.println("Error reading file '" + OUTPUT_FILE + "'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        writeMakerReagentTableData();
    }
}
