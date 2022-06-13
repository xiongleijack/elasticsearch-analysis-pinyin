package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.elasticsearch.analysis.PinyinConfig;
import org.nlpcn.commons.lang.pinyin.Pinyin;

import java.io.IOException;
import java.util.*;

/**
 *
 * @author xionglei
 * @version 1.0
 * @date 2022/6/13 23:15
 */
public class MultiplePinyinTokenFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private boolean done = true;
    private boolean processedCandidate = false;
    private boolean processedFullPinyinLetter = false;
    private boolean processedFirstLetter = false;
    private boolean processedOriginal = false;
    private boolean processedSortCandidate = false;
    protected int position = 0;
    protected int lastOffset = 0;
    private PinyinConfig config;
    List<TermItem> candidate;
    private HashSet<String> termsFilter;

    protected int candidateOffset = 0;
    List<StringBuilder> firstLetters;
    List<StringBuilder> fullPinyinLetters;
    String source;
    private int lastIncrementPosition = 0;

    private PositionIncrementAttribute positionAttr = addAttribute(PositionIncrementAttribute.class);

    public MultiplePinyinTokenFilter(TokenStream in, PinyinConfig config) {
        super(in);
        this.config = config;
        //validate config
        if (!(config.keepFirstLetter || config.keepSeparateFirstLetter || config.keepFullPinyin || config.keepJoinedFullPinyin)) {
            throw new ConfigErrorException("pinyin config error, can't disable separate_first_letter, first_letter and full_pinyin at the same time.");
        }
        candidate = new ArrayList<>();
        firstLetters = new LinkedList<StringBuilder>();
        termsFilter = new HashSet<>();
        fullPinyinLetters = new LinkedList<StringBuilder>();
    }

    //TODO refactor, merge code
    @Override
    public final boolean incrementToken() throws IOException {


        if (!done) {
            if (readTerm()) return true;
        }

        if (done) {
            resetVariable();
            if (!input.incrementToken()) {
                return false;
            }
            done = false;
        }
        readTerm();
        return true;
    }

    private boolean readTerm() {
        if (!processedCandidate) {
            processedCandidate = true;
            lastOffset = termAtt.length();
            source = termAtt.toString();
            if (config.trimWhitespace) {
                source = source.trim();
            }

            List<String> pinyinList = Pinyin.multiplePinyin(source);
            if (pinyinList.size() == 0) return false;

            StringBuilder buff = new StringBuilder();
            int buffStartPosition = 0;
            int buffSize = 0;
            position = 0;

            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);

                //keep original alphabet
                if (c < 128) {
                    if (buff.length() <= 0) {
                        buffStartPosition = i;
                    }
                    if ((c > 96 && c < 123) || (c > 64 && c < 91) || (c > 47 && c < 58)) {
                        if (config.keepNoneChinese) {
                            if (config.keepNoneChinese) {
                                if (config.keepNoneChineseTogether) {
                                    buff.append(c);
                                    buffSize++;
                                } else {
                                    addCandidate(new TermItem(String.valueOf(c), i, i + 1, buffStartPosition));
                                }
                            }
                        }
                        if (config.keepNoneChineseInFirstLetter) {
                            if (firstLetters.size() == 0) {
                                firstLetters.add(new StringBuilder(c + ""));
                            } else {
                                for (int j = 0; j < firstLetters.size(); j++) {
                                    firstLetters.get(j).append(c);
                                }
                            }
                        }
                        if (config.keepNoneChineseInJoinedFullPinyin) {
                            if (fullPinyinLetters.size() == 0) {
                                fullPinyinLetters.add(new StringBuilder(c + ""));
                            } else {
                                for (StringBuilder fullPinyinLetter : fullPinyinLetters) {
                                    fullPinyinLetter.append(c);
                                }
                            }
                        }
                    }
                } else {
                    //clean previous temp
                    if (buff.length() > 0) {
                        buffSize = parseBuff(buff, buffSize, buffStartPosition);
                    }

                    String pinyin = pinyinList.get(i);
                    if (pinyin != null && pinyin.length() > 0) {
                        String[] pingyinList = pinyin.split(" ");
                        position++;
                        if (firstLetters.size() == 0) {
                            if (pingyinList.length > 1) {
                                for (String py : pingyinList) {
                                    firstLetters.add(new StringBuilder(py.substring(0, 1)));
                                }
                            } else {
                                firstLetters.add(new StringBuilder(pinyin.substring(0, 1)));
                            }
                        } else {
                            if (pingyinList.length > 1) {
                                int lettersSize = firstLetters.size();
                                for (int j = 0; j < lettersSize; j++) {
                                    String letter = firstLetters.get(j).toString();
                                    for (String py : pingyinList) {
                                        firstLetters.add(new StringBuilder(letter + py.substring(0, 1)));
                                    }
                                }

                                for (int j = 0; j < lettersSize; j++) {
                                    firstLetters.remove(lettersSize - j - 1);
                                }
                            } else {
                                for (int j = 0; j < firstLetters.size(); j++) {
                                    firstLetters.get(j).append(pinyin.charAt(0));
                                }
                            }
                        }
                        if (config.keepSeparateFirstLetter & pinyin.length() > 1) {
                            addCandidate(new TermItem(String.valueOf(pinyin.charAt(0)), i, i + 1, position));
                        }
                        if (config.keepFullPinyin) {
                            addCandidate(new TermItem(pinyin, i, i + 1, position));
                        }
                        if (config.keepJoinedFullPinyin) {
                            if (fullPinyinLetters.size() == 0) {
                                if (pingyinList.length > 1) {
                                    for (String py : pingyinList) {
                                        fullPinyinLetters.add(new StringBuilder(py));
                                    }
                                } else {
                                    fullPinyinLetters.add(new StringBuilder(pingyinList[0]));
                                }
                            } else {
                                if (pingyinList.length > 1) {
                                    int fullPinyinSize = fullPinyinLetters.size();
                                    for (int j = 0; j < fullPinyinSize; j++) {
                                        String letter = fullPinyinLetters.get(j).toString();
                                        for (String py : pingyinList) {
                                            fullPinyinLetters.add(new StringBuilder(letter + py));
                                        }
                                    }

                                    for (int j = 0; j < fullPinyinSize; j++) {
                                        fullPinyinLetters.remove(fullPinyinSize - j - 1);
                                    }
                                } else {
                                    for (int j = 0; j < fullPinyinLetters.size(); j++) {
                                        fullPinyinLetters.get(j).append(pingyinList[0]);
                                    }
                                }
                            }
                        }
                    }
                }

                lastOffset = i;

            }

            //clean previous temp
            if (buff.length() > 0) {
                buffSize = parseBuff(buff, buffSize, buffStartPosition);
            }
        }


        if (config.keepOriginal && !processedOriginal) {
            processedOriginal = true;
            addCandidate(new TermItem(source, 0, source.length(), 1));
        }

        if (config.keepJoinedFullPinyin && !processedFullPinyinLetter && fullPinyinLetters.size() > 0) {
            processedFullPinyinLetter = true;
            for (StringBuilder fullPinyinLetter : fullPinyinLetters) {
                addCandidate(new TermItem(fullPinyinLetter.toString(), 0, source.length(), 1));
            }
            fullPinyinLetters.clear();
        }


        if (config.keepFirstLetter && firstLetters.size() > 0 && !processedFirstLetter) {
            processedFirstLetter = true;
            for (StringBuilder firstLetter : firstLetters) {
                String fl;
                if (firstLetter.length() > config.LimitFirstLetterLength && config.LimitFirstLetterLength > 0) {
                    fl = firstLetter.substring(0, config.LimitFirstLetterLength);
                } else {
                    fl = firstLetter.toString();
                }
                if (config.lowercase) {
                    fl = fl.toLowerCase();
                }
                if (!(config.keepSeparateFirstLetter && fl.length() <= 1)) {
                    addCandidate(new TermItem(fl, 0, fl.length(), 1));
                }
            }
        }

        if (!processedSortCandidate) {
            processedSortCandidate = true;
            Collections.sort(candidate);
        }

        if (candidateOffset < candidate.size()) {
            TermItem item = candidate.get(candidateOffset);
            candidateOffset++;
            setTerm(item.term, item.startOffset, item.endOffset, item.position);
            return true;
        }

        done = true;
        return false;
    }


    void addCandidate(TermItem item) {

        String term = item.term;
        if (config.lowercase) {
            term = term.toLowerCase();
        }

        if (config.trimWhitespace) {
            term = term.trim();
        }
        item.term = term;

        if (term.length() == 0) {
            return;
        }

        //remove same term with same position
        String fr = term + item.position;

        //remove same term, regardless position
        if (config.removeDuplicateTerm) {
            fr = term;
        }

        if (termsFilter.contains(fr)) {
            return;
        }
        termsFilter.add(fr);

        candidate.add(item);
    }


    void setTerm(String term, int startOffset, int endOffset, int position) {
        if (config.lowercase) {
            term = term.toLowerCase();
        }

        if (config.trimWhitespace) {
            term = term.trim();
        }

        //ignore empty term
        if (term.length() == 0) {
            return;
        }

        termAtt.setEmpty();
        termAtt.append(term);
        if (startOffset < 0) {
            startOffset = 0;
        }
        if (endOffset < startOffset) {
            endOffset = startOffset + term.length();
        }

        int offset = position - lastIncrementPosition;
        if (offset < 0) {
            offset = 0;
        }
        positionAttr.setPositionIncrement(offset);

        lastIncrementPosition = position;
    }

    private int parseBuff(StringBuilder buff, int buffSize, int buffPosition) {
        if (config.keepNoneChinese) {
            if (config.noneChinesePinyinTokenize) {
                List<String> result = PinyinAlphabetTokenizer.walk(buff.toString());
                int start = (lastOffset - buffSize + 1);
                for (int i = 0; i < result.size(); i++) {
                    int end;
                    String t = result.get(i);
                    if (config.fixedPinyinOffset) {
                        end = start + 1;
                    } else {
                        end = start + t.length();
                    }
                    addCandidate(new TermItem(result.get(i), start, end, ++position));
                    start = end;
                }
            } else if (config.keepFirstLetter || config.keepSeparateFirstLetter || config.keepFullPinyin || !config.keepNoneChineseInJoinedFullPinyin) {
                addCandidate(new TermItem(buff.toString(), lastOffset - buffSize, lastOffset, ++position));
            }
        }

        buff.setLength(0);
        buffSize = 0;
        return buffSize;
    }

    @Override
    public final void end() throws IOException {
        super.end();
    }

    void resetVariable() {
        position = 0;
        lastOffset = 0;
        candidate.clear();
        this.processedCandidate = false;
        this.processedFirstLetter = false;
        this.processedFullPinyinLetter = false;
        this.processedOriginal = false;
        firstLetters.clear();
        fullPinyinLetters.clear();
        source = null;
        candidateOffset = 0;
        termsFilter.clear();
        lastIncrementPosition = 0;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.done = true;
        resetVariable();
    }

}
