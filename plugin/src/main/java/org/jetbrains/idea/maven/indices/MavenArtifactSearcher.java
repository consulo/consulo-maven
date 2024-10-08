/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.server.MavenServerIndexer;

import java.util.*;

public class MavenArtifactSearcher extends MavenSearcher<MavenArtifactSearchResult> {
    public static final String TERM = MavenServerIndexer.SEARCH_TERM_COORDINATES;

    @Override
    protected Pair<String, Query> preparePatternAndQuery(String pattern) {
        pattern = pattern.toLowerCase();
        if (pattern.trim().length() == 0) {
            return Pair.create(pattern, (Query)new MatchAllDocsQuery());
        }

        List<String> parts = new ArrayList<>();
        for (String each : StringUtil.tokenize(pattern, " :")) {
            parts.add(each);
        }

        BooleanQuery query = new BooleanQuery();

        if (parts.size() == 1) {
            query.add(new WildcardQuery(new Term(TERM, "*" + parts.get(0) + "*|*|*|*")), BooleanClause.Occur.SHOULD);
            query.add(new WildcardQuery(new Term(TERM, "*|*" + parts.get(0) + "*|*|*")), BooleanClause.Occur.SHOULD);
        }
        if (parts.size() == 2) {
            query.add(
                new WildcardQuery(new Term(TERM, "*" + parts.get(0) + "*|*" + parts.get(1) + "*|*|*")),
                BooleanClause.Occur.SHOULD
            );
            query.add(
                new WildcardQuery(new Term(TERM, "*" + parts.get(0) + "*|*|" + parts.get(1) + "*|*")),
                BooleanClause.Occur.SHOULD
            );
            query.add(
                new WildcardQuery(new Term(TERM, "*|*" + parts.get(0) + "*|" + parts.get(1) + "*|*")),
                BooleanClause.Occur.SHOULD
            );
        }
        if (parts.size() >= 3) {
            String s = "*" + parts.get(0) + "*|*" + parts.get(1) + "*|" + parts.get(2) + "*|*";
            query.add(new WildcardQuery(new Term(TERM, s)), BooleanClause.Occur.MUST);
        }

        return Pair.create(pattern, (Query)query);
    }

    @Override
    protected Collection<MavenArtifactSearchResult> processResults(Set<MavenArtifactInfo> infos, String pattern, int maxResult) {
        Map<String, MavenArtifactSearchResult> result = new HashMap<>();

        for (MavenArtifactInfo each : infos) {
            if (!StringUtil.isEmptyOrSpaces(each.getClassifier())) {
                continue; // todo skip for now
            }

            String key = makeKey(each);
            MavenArtifactSearchResult searchResult = result.get(key);
            if (searchResult == null) {
                searchResult = new MavenArtifactSearchResult();
                result.put(key, searchResult);
            }
            searchResult.versions.add(each);
        }

        return result.values();
    }
}
