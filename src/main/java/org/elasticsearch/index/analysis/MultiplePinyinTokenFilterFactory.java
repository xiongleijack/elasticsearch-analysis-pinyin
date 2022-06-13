package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.analysis.PinyinConfig;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;

/**
 * @author xionglei
 * @version 1.0
 * @date 2022/6/13 23:16
 */
public class MultiplePinyinTokenFilterFactory extends AbstractTokenFilterFactory {

    private PinyinConfig config;

    public MultiplePinyinTokenFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
        config = new PinyinConfig(settings);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new MultiplePinyinTokenFilter(tokenStream, config);
    }

}
