package com.ijiaodui.diff.pdfdiff;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public class AiBiDuiPdfDiffProc extends AiBiDuiDiffProc{

    static class Position {
        public int pageNum;
        public char chr;
        public float x;
        public float y;
        public float width;
        public float height;
    }

    static class OriginTarget {
        public String value;
        public List<Position> position;
    }

    static class Differences {
        public String status;
        public OriginTarget origin;
        public OriginTarget target;
    }

    static class Data {
        public String similarity;
        public int addedCount;
        public int deletedCount;
        public int modifiedCount;
        public List<Differences> differences;
    }
    
    static class OutPut {
        int code; 
        String msg;
        Data data;
    }


    public static List<Difference> getPdfFormatDiff(String srcPdfFilePath, String cmpPdfFilePath) {
        AiBiDuiDiffProc difProc = new AiBiDuiDiffProc();
        List<Difference> differences = new ArrayList<>();
        try {
            AiBiDuiPDFTextStripper srcPdfStripper = new AiBiDuiPDFTextStripper();
            File srcFile = new File(srcPdfFilePath);
            PDDocument srcDoc = Loader.loadPDF(srcFile);
            srcPdfStripper.strip(srcDoc);
            srcDoc.close();
            
            AiBiDuiPDFTextStripper cmpPdfStripper = new AiBiDuiPDFTextStripper();
            File cmpFile = new File(cmpPdfFilePath);
            PDDocument cmpDoc = Loader.loadPDF(cmpFile);
            cmpPdfStripper.strip(cmpDoc);
            cmpDoc.close();
            
            differences = difProc.aiGetFormatDiff(srcPdfStripper, cmpPdfStripper);
        }
        catch (IOException e) {
            e.printStackTrace();
        }            
        return differences;
    }


    public static String getPdfDiffJsonString(String srcPdfFilePath, String cmpPdfFilePath) {
        String jsonOutput = ""; // Return value

        String similarity = "";
        int addedCount = 0, deletedCount = 0, modifiedCount = 0;
        
        AiBiDuiDiffProc difProc = new AiBiDuiDiffProc();
        List<Difference> differences = new ArrayList<>();
        
        try {
            AiBiDuiPDFTextStripper srcPdfStripper = new AiBiDuiPDFTextStripper();
            File srcFile = new File(srcPdfFilePath);
            PDDocument srcDoc = Loader.loadPDF(srcFile);
            srcPdfStripper.strip(srcDoc);
            srcDoc.close();
            
            AiBiDuiPDFTextStripper cmpPdfStripper = new AiBiDuiPDFTextStripper();
            File cmpFile = new File(cmpPdfFilePath);
            PDDocument cmpDoc = Loader.loadPDF(cmpFile);
            cmpPdfStripper.strip(cmpDoc);
            cmpDoc.close();
      
            String text1 = srcPdfStripper.getText();
            String text2 = cmpPdfStripper.getText();

            LinkedList<DiffMatch.Diff> diff = difProc.getDiff(text1, text2);
            similarity = difProc.getSimilarityStr(diff);

            differences = difProc.aiGetFormatDiff(srcPdfStripper, cmpPdfStripper);

            for (AiBiDuiDiffProc.Difference d : differences) {
                switch (d.status) {
                    case DELETE:
                        deletedCount ++;                        
                        break;
                    case INSERT:
                        addedCount ++;
                        break;
                    case MODIFY:
                        modifiedCount ++;
                        break;               
                    default:
                        break;
                }
            }
            
            System.out.println("addCount: " + addedCount);
            System.out.println("deletedCount: " + deletedCount);
            System.out.println("modifiedCount: " + modifiedCount);
            System.out.println("Similarity: " + similarity);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        // Convert to Json
        if (differences.size() > 0) {
            OutPut outPut = new OutPut();
            outPut.code = 200;
            outPut.msg = "success";
            
            Data data = new Data();
            data.similarity = similarity;
            data.addedCount = addedCount;
            data.deletedCount = deletedCount;
            data.modifiedCount = modifiedCount;

            List<Differences> differenceList = new ArrayList<>();
            for (AiBiDuiDiffProc.Difference d : differences) {
                Differences difference = new Differences();
                switch (d.status) {
                    case DELETE:
                        difference.status = "DELETE";
                        break;
                    case INSERT:
                        difference.status = "ADD";
                        break;
                    case MODIFY:
                        difference.status = "MODIFY";
                        break;                
                    default:
                        difference.status = "UNKNOW";
                        break;
                }

                OriginTarget origin = new OriginTarget();
                if (d.origin != null) {
                    origin.value = d.origin.value;
                    List<Position> positionList = new ArrayList<>();
                    for (TextPosition p : d.origin.positions) {
                        Position pos = new Position();
                        pos.pageNum = p.pagNum;
                        pos.chr = p.chr;
                        pos.x = p.x;
                        pos.y = p.y;
                        pos.width = p.width;
                        pos.height = p.height;                        
                        positionList.add(pos);
                    } 
                    origin.position = positionList;
                }

                OriginTarget target = new OriginTarget();
                if (d.target != null) {
                    target.value = d.target.value;
                    List<Position> positionList = new ArrayList<>();
                    for (TextPosition p : d.target.positions) {
                        Position pos = new Position();
                        pos.pageNum = p.pagNum;
                        pos.chr = p.chr;
                        pos.x = p.x;
                        pos.y = p.y;
                        pos.width = p.width;
                        pos.height = p.height;                        
                        positionList.add(pos);
                    } 
                    target.position = positionList;
                }
                difference.origin = origin;
                difference.target = target;
                differenceList.add(difference);
            }
            data.differences = differenceList;
            outPut.data = data;

            Gson gson = new Gson();
            //Gson gson = new GsonBuilder().setPrettyPrinting().create();
            jsonOutput = gson.toJson(outPut);
            jsonOutput = jsonOutput.replace("\"chr\"", "\"char\"");
        } 
        return jsonOutput;
    }


    public static JsonElement getPdfDiffGsonElement(String json) {
        JsonElement gsonElement = JsonParser.parseString(json); 
        return gsonElement;
    }


    public static com.google.gson.JsonObject getPdfDiffGsonObject(String json) {
        com.google.gson.JsonObject gsonObject = new Gson().fromJson(json, com.google.gson.JsonObject.class);
        return gsonObject;
    }


    public static JsonObject getPdfDiffVertxJsonObject(String srcPdfFilePath, String cmpPdfFilePath) {
        String json = getPdfDiffJsonString(srcPdfFilePath, cmpPdfFilePath);
        com.google.gson.JsonObject gsonObject = getPdfDiffGsonObject(json);
        JsonObject vertxJsonObject = gsonToVertx(gsonObject);
        return vertxJsonObject;
    }


    private static JsonObject gsonToVertx(com.google.gson.JsonObject gJsonObject) {
        JsonObject vJsonObject = new JsonObject();

        for (String key : gJsonObject.keySet()) {
            JsonElement element = gJsonObject.get(key);
            
            if (element.isJsonPrimitive()) {
                // 处理基本类型
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isString()) {
                    vJsonObject.put(key, primitive.getAsString());
                } else if (primitive.isBoolean()) {
                    vJsonObject.put(key, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    vJsonObject.put(key, primitive.getAsNumber());
                }
            } else if (element.isJsonObject()) {
                // 递归转换嵌套JsonObject
                vJsonObject.put(key, gsonToVertx(element.getAsJsonObject()));
            } else if (element.isJsonArray()) {
                // 转换JsonArray
                com.google.gson.JsonArray gsonArray = element.getAsJsonArray();
                JsonArray vertxArray = new JsonArray();
                for (JsonElement arrElement : gsonArray) {
                    if (arrElement.isJsonObject()) {
                        vertxArray.add(gsonToVertx(arrElement.getAsJsonObject()));
                    } else {
                        vertxArray.add(arrElement.getAsString());
                    }
                }
                vJsonObject.put(key, vertxArray);
            }
        }        
        return vJsonObject;
    }


}
