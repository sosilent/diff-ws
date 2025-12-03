package com.ijiaodui.diff.pdfdiff;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AiBiDuiPDFTextStripper extends PDFTextStripper {

    public AiBiDuiPDFTextStripper() throws IOException {

    }

    public static class CharPosition {
        public char chr;
        public float x;
        public float y_orgin;
        public float y;
        public float width;
        public float height;
        public float fontSize;
        public int page;
    }

    private List<CharPosition> charPositions = new ArrayList<>();
    private String text = "";
    private int currentPage;

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (charPositions.size() > 0) {
            //if (Math.abs(textPositions.get(0).getYDirAdj() - charPositions.get(charPositions.size()-1).y) > charPositions.get(charPositions.size()-1).height / 2) {
            if (Math.abs(textPositions.get(0).getYDirAdj() - (charPositions.get(charPositions.size()-1).y_orgin)) > charPositions.get(charPositions.size()-1).height / 2) {
                CharPosition charPosition = new CharPosition();
                charPosition.chr = '\n';
                charPosition.x = 0;
                charPosition.y = 0;
                charPosition.width = 0;
                charPosition.height = 0;
                charPosition.fontSize = 0;
                charPosition.page = 0;
                this.charPositions.add(charPosition);
                this.text += charPosition.chr;
            }
        }
        int charIndex = 0;
        for (TextPosition position : textPositions) {
            CharPosition charPosition = new CharPosition();
            charPosition.fontSize = position.getFontSize();
            charPosition.page = this.currentPage;
            charPosition.chr = text.charAt(charIndex);
            charPosition.x = position.getXDirAdj();
            charPosition.y_orgin = position.getYDirAdj();
            charPosition.y = charPosition.y_orgin - position.getTextMatrix().getScaleY();   ///
            charPosition.width = position.getWidthDirAdj();
            //charPosition.height = position.getTextMatrix().getScaleY();
            charPosition.height = position.getHeight() * 2;
            /*
            PDFont font = position.getFont();
            PDFontDescriptor fontDesc = font.getFontDescriptor();
            if (fontDesc != null) {
                float ascent = fontDesc.getAscent();   
                float descent = fontDesc.getDescent(); 
                float designHeight = (ascent - descent) / 1000f; 
                float yScale = position.getYScale();
                float realHeight = designHeight  * yScale;
                charPosition.height = realHeight;
            }
            */

            charIndex ++;
            this.charPositions.add(charPosition);
            this.text += charPosition.chr;
        }
        output.write(text);
    }

    public void strip(PDDocument doc){
        String text = "";
        try {
            //this.setSortByPosition(true);
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                setStartPage(page);
                setEndPage(page);
                this.currentPage = page;
                text = getText(doc);
            }
            //this.text = text;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getText() {
        return this.text;
    }

    public List<CharPosition> getCharPositions() {
        return this.charPositions;
    }

}
