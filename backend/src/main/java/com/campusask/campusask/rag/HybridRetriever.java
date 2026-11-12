package com.campusask.campusask.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** Pure retrieval stage; lexical fallback remains useful during embedding outages/model changes. */
@Component
public class HybridRetriever {
    public record Hit(RagStore.Chunk chunk,double score) {}
    private final ObjectMapper json=new ObjectMapper();
    public List<Hit> retrieve(String query,double[] vector,List<RagStore.Chunk> chunks,String version,int limit) {
        Set<String> terms=TextChunks.terms(query);
        return chunks.stream().map(c -> {
            Set<String> words=TextChunks.terms(c.title()+"\n"+c.text());
            double lexical=terms.stream().filter(words::contains).count()/(double)Math.max(1,terms.size());
            double score=lexical;
            if (vector.length>0 && version.equals(c.model())) {
                try {
                    double[] candidate=json.readValue(c.vector(),double[].class);
                    if (vector.length==candidate.length) {
                        double semantic=Math.max(0,TextChunks.cosine(vector,candidate));
                        score=Math.max(lexical,.8*semantic+.2*lexical);
                    }
                } catch (Exception ignored) { /* Keep lexical retrieval for invalid stored vectors. */ }
            }
            return new Hit(c,score);
        }).filter(h->h.score()>=.30).sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(limit).toList();
    }
}
