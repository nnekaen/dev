package com.w6.nlp;

import com.sun.javafx.scene.control.skin.VirtualFlow;
import com.w6.data.ObjectsAndSubjects;
import com.w6.data.Table;
import com.w6.data.Response;
import com.w6.data.Word;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;
import java.io.IOException;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Parser {    
    static LexicalizedParser lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
    static ViolentVerbsParser violentVerbsParser;
    static WeaponsParser weaponsParser;
    private TokenizerFactory<CoreLabel> tokenizerFactory;
    
    public Parser() throws IOException{
        tokenizerFactory =
                PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
        violentVerbsParser = new ViolentVerbsParser(lp);
        weaponsParser = new WeaponsParser(lp);
    }
    
    public Response generateResponse(final String input) {

        List<String> who = new ArrayList<String>();
        List<String> weapon = new ArrayList<String>();
        List<String> whom = new ArrayList<String>();
        List<String> where = new ArrayList<String>();
        List<String> when = new ArrayList<String>();
        List<String> what = new ArrayList<String>();
        List<Word> text = new ArrayList<Word>();
        
        List<Pair<String,Integer>> ratedWhen = new ArrayList<>();
        List<Pair<String,Integer>> ratedWhere = new ArrayList<>();
        
        ArrayList<String> dateTimeTags = new ArrayList<>();
        dateTimeTags.add("DATE");
        dateTimeTags.add("TIME");
        ArrayList<String> locationTags = new ArrayList<>();
        locationTags.add("LOCATION");
       
        
        Document document = new Document(input);
        for (Sentence sentence : document.sentences())
        {
            List<String> sentenseWho = new ArrayList<String>();
            List<String> sentenseWeapon = new ArrayList<String>();
            List<String> sentenseWhom = new ArrayList<String>();
            List<String> sentenseWhere = new ArrayList<String>();
            List<String> sentenseWhen = new ArrayList<String>();
            List<String> sentenseWhat = new ArrayList<String>();
            
            
            
            
            Tree parse = lp.apply(
                    tokenizerFactory.getTokenizer(new StringReader(sentence.text()))
                        .tokenize()
            );
            
            sentenseWhat = violentVerbsParser.getAllViolentVerbs(parse);
            
            sentenseWhen = DateTimeParser.parseDateAndTimeFromString(
                sentence, 
                dateTimeTags
            );
            
            sentenseWhere = DateTimeParser.parseDateAndTimeFromString(
                sentence, 
                locationTags
            );
            
            int typeOfSentence = 1;
            sentenseWeapon = weaponsParser.getAllWeapons(parse);
            
            if (!sentenseWhat.isEmpty())
            {
                ObjectsAndSubjects objAndSubj = GetDoerAndVictim.getSubjectAndObjectOfViolence(parse,sentenseWhat);
                sentenseWho.addAll(objAndSubj.subjects);
                sentenseWhom.addAll(objAndSubj.objects);
                typeOfSentence = 2;
            }  
            
            addValueToRatedArray(typeOfSentence, ratedWhere, where);
            addValueToRatedArray(typeOfSentence, ratedWhen, when);
            
            
            for (Tree leaf : parse.getLeaves()) {
                Tree parent = leaf.parent(parse);
                String label = "";
                String word = leaf.label().value();
                if (sentenseWho.contains(word))
                {
                    label = "who";
                } else 
                if (sentenseWhat.contains(word))
                {
                    label = "what";
                } else 
                if (sentenseWhere.contains(word))
                {
                    label = "where";
                } else 
                if (sentenseWhom.contains(word))
                {
                    label = "whom";
                } else 
                if (sentenseWhen.contains(word))
                {
                    label = "when";
                } else
                if (sentenseWeapon.contains(word))
                {
                    label = "weapon";
                }
                text.add(new Word(word, label));
            }
            
            
            
            who.addAll(sentenseWho);
            what.addAll(sentenseWhat);
            where.addAll(sentenseWhere);
            whom.addAll(sentenseWhom);
            when.addAll(sentenseWhen);
            weapon.addAll(sentenseWeapon);
        }
        
        

        removeEquals(who);
        removeEquals(what);
        removeEquals(whom);
        removeEquals(weapon);
        
        
        removeAndCountRatedEquals(ratedWhen,when);
        removeAndCountRatedEquals(ratedWhere,where);


        
        return new Response(text, new Table(who, weapon, what, whom, where, when));
    }
    
    private void removeEquals(
            List<String> list
    ) {
        
       Set<String> all = new HashSet<String>(list);
       list.clear();
       list.addAll(all);
        
    }
    
    private void removeAndCountRatedEquals(List<Pair<String,Integer>> ratedList, List<String> list)
    {
        Map<String, Pair<Integer,Integer>> statMap = new HashMap<>();
        for(Pair<String,Integer> pair : ratedList)
        {
            String word = pair.first;
            Integer value = pair.second;

            if(statMap.containsKey(word)){
                statMap.replace(word,
                        new Pair<>(
                                statMap.get(word).first+1,
                                statMap.get(word).second + value
                        )
                );
            } else {
                statMap.put(
                        word,
                        new Pair<>(1, value)
                );
            }
        }
        ratedList.clear();

        ArrayList<Pair<String, Double>> values = new ArrayList<>();

        for(String string : statMap.keySet()){
            Pair<Integer, Integer> val = statMap.get(string);
            values.add(new Pair<>(string, (1.0 *val.second)/val.first));
        }

        Collections.sort(values, new WordComparator());

        list.clear();

        for(Pair<String, Double> p : values){
            list.add(p.first);
        }
        
    }
    
    private void addValueToRatedArray(int value, List<Pair<String,Integer>> where, List<String> what){
        for(String string : what){
            where.add(new Pair<>(string, value));
        }
    }
    
    class WordComparator implements Comparator<Pair<String, Double>>{
        @Override
        public int compare(Pair<String, Double> a, Pair<String, Double> b) {
            return a.second < b.second ? 1 : a.second == b.second ? 0 : -1;
        }
    }
}