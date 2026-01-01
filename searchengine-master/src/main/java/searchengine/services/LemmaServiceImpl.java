package searchengine.services;


import org.apache.lucene.morphology.russian.RussianMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LemmaServiceImpl implements LemmaService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final RussianMorphology morphology;

    public LemmaServiceImpl(LemmaRepository lemmaRepository,
                            IndexRepository indexRepository) throws Exception {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.morphology = new RussianMorphology();
    }

    @Override
    @Transactional
    public void processPage(Page page, Site site) {

        String text = Jsoup.parse(page.getContent()).text().toLowerCase();

        String[] words = text.replaceAll("[^а-яё\\s]", " ")
                .trim()
                .split("\\s+");

        Map<String, Integer> lemmaCounts = new HashMap<>();

        for (String word : words) {
            if (word.length() < 3) continue;

            List<String> forms = morphology.getNormalForms(word);
            if (forms.isEmpty()) continue;

            String lemma = forms.get(0);
            lemmaCounts.put(lemma, lemmaCounts.getOrDefault(lemma, 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : lemmaCounts.entrySet()) {

            String lemmaValue = entry.getKey();
            int count = entry.getValue();

            Lemma lemma = lemmaRepository.findBySiteAndLemma(site, lemmaValue)
                    .orElseGet(() -> {
                        Lemma l = new Lemma();
                        l.setLemma(lemmaValue);
                        l.setSite(site);
                        l.setFrequency(0);
                        return l;
                    });

            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            IndexEntity index = new IndexEntity();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) count);
            indexRepository.save(index);
        }
    }
}

