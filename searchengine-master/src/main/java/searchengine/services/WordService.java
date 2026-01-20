package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WordService {

    @Autowired
    private LuceneMorphology luceneMorphology;

    public String splitTextIntoWords(String text) {

        StringBuilder stringBuilder = new StringBuilder();
        String str;
        String regex = "[[a-z][A-Z][а-я][А-Я][\\s]?]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            str = text.substring(start,end);
            stringBuilder.append(str.toLowerCase());
        }
        str = stringBuilder.toString();
        stringBuilder.delete(0,stringBuilder.length());
        String[] massive = str.split("\\s+");
        for (int i = 0; i < massive.length; i++) {

            if (isCorrectWord(massive[i])) {
                stringBuilder.append(massive[i]).append(" ");
            }
        }
        if (!stringBuilder.toString().isBlank())
            stringBuilder.delete(stringBuilder.lastIndexOf(" "), stringBuilder.length());

        return stringBuilder.toString();
    }

    //(за исключением: междометий, союзов, предлогов и частиц
    public boolean isCorrectWord(String word){
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> cleanText = luceneMorph.getMorphInfo(word);
            String morph = cleanText.get(0).split(" ")[1];
            return  (morph.equals("МЕЖД") || morph.equals("СОЮЗ") || morph.equals("ПРЕДЛ") || morph.equals("ЧАСТ"))? false : true;
        } catch (WrongCharaterException | IOException e) {
            return false;
        }
    }

    public Map<String, Integer> getLemmasMap(String text) {
        Map<String, Integer> lemmasMap = new WeakHashMap<>();
        List<String> list = List.of(text.trim().split(" "));

        for (String word : list) {
            List<String> morphWordsList = luceneMorphology.getNormalForms(word);

            morphWordsList.forEach(morphWord -> {
                int count = lemmasMap.containsKey(morphWord) ? lemmasMap.get(morphWord) + 1 : 1;
                lemmasMap.put(morphWord, count);
            });
        }
        list.clear();
        return lemmasMap;
    }

    public String  deleteTagsFromContent(String content){
        return content == null? "" : Jsoup.parse(content).text();
    }
}
