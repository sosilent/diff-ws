package com.ijiaodui.diff.pdfdiff;

import java.util.*;

public class AiBiDuiDiffProc extends DiffProc {  

  static enum Status {
    DELETE, INSERT, EQUAL, MODIFY
  }


  static class TextPosition {
    public int pagNum;
    public char chr;
    public float x;
    public float y;
    public float width;
    public float height;
  }
  
  static class DiffInfo {
    public String value;
    public List<TextPosition> positions;
  }

  static class Difference {
    public Status status;
    public DiffInfo origin;
    public DiffInfo target;
  }


  public List<Difference> aiGetDiff(AiBiDuiPDFTextStripper stripper1, AiBiDuiPDFTextStripper stripper2) {
    List<Difference> differences = new ArrayList<>();
    String text1 = stripper1.getText();
    String text2 = stripper2.getText();
    List<AiBiDuiPDFTextStripper.CharPosition> charPositions1 = stripper1.getCharPositions();
    List<AiBiDuiPDFTextStripper.CharPosition> charPositions2 = stripper2.getCharPositions();

    if (text1.length() != charPositions1.size() || text2.length() != charPositions2.size()) {
      System.out.println("Stripper input ERROR!");
      return differences;
    }
    System.out.println("Calculating Differences...");

    LinkedList<DiffMatch.Diff> textDiff = getDiff(text1, text2);

    int charIndex1 = 0, charIndex2 = 0;
    for (DiffMatch.Diff d : textDiff) {
      Difference difference = new Difference();
      List<TextPosition> positions = new ArrayList<>();
      String txt = d.text;
      switch (d.operation) {
        case EQUAL:
          difference.status = Status.EQUAL;
          difference.origin = new DiffInfo();
          difference.origin.value = txt;
          for (int i=0; i<txt.length(); i++) {
            AiBiDuiPDFTextStripper.CharPosition charPosition = charPositions1.get(charIndex1);
            TextPosition position = new TextPosition();
            position.pagNum = charPosition.page;
            position.chr = charPosition.chr;
            position.x = charPosition.x;
            position.y = charPosition.y;
            position.width = charPosition.width;
            position.height = charPosition.height;
            positions.add(position);
            charIndex1 ++;
            charIndex2 ++;
          }
          difference.origin.positions = positions;
          break;

        case DELETE:
          difference.status = Status.DELETE;
          difference.origin = new DiffInfo();
          difference.origin.value = txt;
          for (int i=0; i<txt.length(); i++) {
            AiBiDuiPDFTextStripper.CharPosition charPosition = charPositions1.get(charIndex1);
            TextPosition position = new TextPosition();
            position.pagNum = charPosition.page;
            position.chr = charPosition.chr;
            position.x = charPosition.x;
            position.y = charPosition.y;
            position.width = charPosition.width;
            position.height = charPosition.height;
            positions.add(position);
            charIndex1 ++;
          }
          difference.origin.positions = positions;
          break;

        case INSERT:
          difference.status = Status.INSERT;
          difference.target = new DiffInfo();
          difference.target.value = txt;
          for (int i=0; i<txt.length(); i++) {
            AiBiDuiPDFTextStripper.CharPosition charPosition = charPositions2.get(charIndex2);
            TextPosition position = new TextPosition();
            position.pagNum = charPosition.page;
            position.chr = charPosition.chr;
            position.x = charPosition.x;
            position.y = charPosition.y;
            position.width = charPosition.width;
            position.height = charPosition.height;
            positions.add(position);
            charIndex2 ++;
          }
          difference.target.positions = positions;
          break;
      }
      differences.add(difference);
    }
    return differences;
  }


  public boolean aiPurifyDiff(List<Difference> differences) {
    if (differences.size() == 0) {
      return false;
    }    
    // Get rid of ENTER
    for (Difference diff : differences) {
      if (diff.origin != null) {
        List<TextPosition> positions = diff.origin.positions;
        if (positions.size() > 0) {
          diff.origin.value = diff.origin.value.replace("\n", "");
          Iterator<TextPosition> iterator = positions.iterator();
          while (iterator.hasNext()) {
            TextPosition pos = iterator.next();
            if (pos.chr == '\n') {
              iterator.remove();
            }
          }
        }
      }
      if (diff.target != null) {
        List<TextPosition> positions = diff.target.positions;
        if (positions.size() > 0) {
          diff.target.value = diff.target.value.replace("\n", "");
          Iterator<TextPosition> iterator = positions.iterator();
          while (iterator.hasNext()) {
            TextPosition pos = iterator.next();
            if (pos.chr == '\n') {
              iterator.remove();
            }
          }
        }
      }
    }
    // Get rid of empty Info
    Iterator<Difference> iterator = differences.iterator();
    while (iterator.hasNext()) {
      Difference diff = iterator.next();
      if (diff.origin != null) {
        String value = diff.origin.value;
        value = value.replace(" ", "");
        if (value == "") {
          diff.origin = null;
        }
      }
      if (diff.target != null) {
        String value = diff.target.value;
        value = value.replace(" ", "");
        if (value == "") {
          diff.target = null;
        }
      }
      if (diff.origin == null && diff.target == null) {
        iterator.remove();
      }
    }
    return true;
  }


  public boolean aiFormatDiff(List<Difference> differences) {
    if (differences.size() == 0) {
      return false;
    }

    // If the first item status is INSERT 
    if (differences.get(0).status == Status.INSERT) {
      if (differences.size()>1) {
        Difference sourceDiff = differences.get(1);
        List<TextPosition> sourcePositions = sourceDiff.origin.positions;
        TextPosition sourcePosition = sourcePositions.get(0);
        TextPosition position = new TextPosition();
        position.pagNum = sourcePosition.pagNum;
        position.chr = '\0';
        position.x = sourcePosition.x;
        position.y = sourcePosition.y;
        position.width = 0;
        position.height = sourcePosition.height;
        List<TextPosition> positions = new ArrayList<>();
        positions.add(position);
        DiffInfo diffInfo = new DiffInfo();
        diffInfo.value = String.valueOf(position.chr);
        diffInfo.positions = positions;
        differences.get(0).origin = diffInfo;
      }
    }

    for (int i = 0; i < differences.size()-1; i++) {
      // Refille INSERT items.
      Difference diff = differences.get(i+1);
      if (diff.status == Status.INSERT) {
          Difference sourceDiff = differences.get(i);
          List<TextPosition> sourcePositions = sourceDiff.origin.positions;
          TextPosition sourcePosition = sourcePositions.get(sourcePositions.size()-1);
          TextPosition position = new TextPosition();
          position.pagNum = sourcePosition.pagNum;
          position.chr = sourcePosition.chr;
          position.x = sourcePosition.x;
          position.y = sourcePosition.y;
          position.width = sourcePosition.width;
          position.height = sourcePosition.height;

          List<TextPosition> positions = new ArrayList<>();
          positions.add(position);

          DiffInfo diffInfo = new DiffInfo();
          diffInfo.value = String.valueOf(position.chr);
          diffInfo.positions = positions;
          diff.origin = diffInfo;
      }

      // Make MODIFY items.
      if (differences.get(i).status == Status.DELETE && differences.get(i+1).status == Status.INSERT) {
        Difference modifyDiff = differences.get(i);
        modifyDiff.status = Status.MODIFY;
        Difference targetDiff = differences.get(i+1);
        modifyDiff.target = targetDiff.target;
        targetDiff.origin = null;
        targetDiff.target = null;        
      }
    }

    this.aiPurifyDiff(differences);
    // Get rid of EQUAL items.
    Iterator<Difference> iterator = differences.iterator();
    while (iterator.hasNext()) {
      Difference diff = iterator.next();
      if (diff.status == Status.EQUAL) {
        iterator.remove();
      }
    }
    return true;
  }


  public List<Difference> aiGetFormatDiff(AiBiDuiPDFTextStripper stripper1, AiBiDuiPDFTextStripper stripper2) {
    List<AiBiDuiDiffProc.Difference> differences = this.aiGetDiff(stripper1, stripper2);
    this.aiPurifyDiff(differences);
    this.aiFormatDiff(differences);
    return differences;
  }


}
