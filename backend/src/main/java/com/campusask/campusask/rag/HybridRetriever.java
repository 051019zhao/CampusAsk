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
            double[] candidate;
            try {candidate=json.readValue(c.vector(),double[].class);} catch(Exception ignored) {candidate=new double[0];}
            boolean compatible=vector.length>0 && vector.length==candidate.length && version.equals(c.model());
            double score=compatible?.8*Math.max(0,TextChunks.cosine(vector,candidate))+.2*lexical:lexical;
            return new Hit(c,score);
        }).filter(h->h.score()>=.30).sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(limit).toList();
    }
}
