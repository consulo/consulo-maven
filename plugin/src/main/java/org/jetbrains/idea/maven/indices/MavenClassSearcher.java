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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.server.MavenServerIndexer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MavenClassSearcher extends MavenSearcher<MavenClassSearchResult> {
    public static final String TERM = MavenServerIndexer.SEARCH_TERM_CLASS_NAMES;

    @Override
    protected Pair<String, Query> preparePatternAndQuery(String pattern) {
        pattern = pattern.toLowerCase();
        if (pattern.trim().length() == 0) {
            return new Pair<>(pattern, new MatchAllDocsQuery());
        }

        List<String> parts = StringUtil.split(pattern, ".");

        StringBuilder newPattern = new StringBuilder();
        for (int i = 0; i < parts.size() - 1; i++) {
            String each = parts.get(i);
            newPattern.append(each.trim());
            newPattern.append("*.");
        }

        String className = parts.get(parts.size() - 1);
        boolean exactSearch = className.endsWith(" ");
        newPattern.append(className.trim());
        if (!exactSearch) {
            newPattern.append("*");
        }

        pattern = newPattern.toString();
        String queryPattern = "*/" + pattern.replaceAll("\\.", "/");

        return new Pair<>(pattern, new WildcardQuery(new Term(TERM, queryPattern)));
    }

    @Override
    protected Collection<MavenClassSearchResult> processResults(Set<MavenArtifactInfo> infos, String pattern, int maxResult) {
        if (pattern.length() == 0 || pattern.equals("*")) {
            pattern = "^/(.*)$";
        }
        else {
            pattern = pattern.replace(".", "/");

            int lastDot = pattern.lastIndexOf("/");
            String packagePattern = lastDot == -1 ? "" : (pattern.substring(0, lastDot) + "/");
            String classNamePattern = lastDot == -1 ? pattern : pattern.substring(lastDot + 1);

            packagePattern = packagePattern.replaceAll("\\*", ".*?");
            classNamePattern = classNamePattern.replaceAll("\\*", "[^/]*?");

            pattern = packagePattern + classNamePattern;

            pattern = ".*?/" + pattern;
            pattern = "^(" + pattern + ")$";
        }
        Pattern p;
        try {
            p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        }
        catch (PatternSyntaxException e) {
            return Collections.emptyList();
        }

        Map<String, MavenClassSearchResult> result = new HashMap<>();
        for (MavenArtifactInfo each : infos) {
            if (each.getClassNames() == null) {
                continue;
            }

            Matcher matcher = p.matcher(each.getClassNames());
            while (matcher.find()) {
                String classFQName = matcher.group(1);
                classFQName = classFQName.replace("/", ".");
                if (classFQName.startsWith(".")) {
                    classFQName = classFQName.substring(1);
                }

                String key = makeKey(classFQName, each);

                MavenClassSearchResult classResult = result.get(key);
                if (classResult == null) {
                    classResult = new MavenClassSearchResult();
                    int pos = classFQName.lastIndexOf(".");
                    if (pos == -1) {
                        classResult.packageName = "default package";
                        classResult.className = classFQName;
                    }
                    else {
                        classResult.packageName = classFQName.substring(0, pos);
                        classResult.className = classFQName.substring(pos + 1);
                    }
                    result.put(key, classResult);
                }

                classResult.versions.add(each);

                if (result.size() > maxResult) {
                    break;
                }
            }
        }

        return result.values();
    }

    @Override
    protected String makeSortKey(MavenClassSearchResult result) {
        return makeKey(result.className, result.versions.get(0));
    }

    private String makeKey(String className, MavenArtifactInfo info) {
        return className + " " + super.makeKey(info);
    }
}