/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.File;

public class TestSonyAssuranceParse {
    public static void main(String[] args) throws Exception {
        File input = new File("/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/reference/insurance/契約内容照会 U4217333.html");
        Document doc = Jsoup.parse(input, "Shift_JIS", "");
        System.out.println("Title: " + doc.title());
        Element noEl = doc.getElementById("contentsform:lblChdrno");
        System.out.println("No: " + (noEl != null ? noEl.text() : "null"));
        Element pNameEl = doc.getElementById("lblDisplayGameId");
        System.out.println("lblDisplayGameId: " + (pNameEl != null ? pNameEl.text() : "null"));
        Element premiumEl = doc.getElementById("contentsform:zpremamt");
        System.out.println("Premium: " + (premiumEl != null ? premiumEl.text() : "null"));
        Element payplanEl = doc.getElementById("contentsform:payplan");
        System.out.println("Payplan: " + (payplanEl != null ? payplanEl.text() : "null"));
        // let's try to get all th with class "__ttl-lv2 __middle"
        for (Element e : doc.select("th.__ttl-lv2.__middle")) {
            System.out.println("TH: " + e.text() + " -> TD: " + e.nextElementSibling().text());
        }
    }
}
