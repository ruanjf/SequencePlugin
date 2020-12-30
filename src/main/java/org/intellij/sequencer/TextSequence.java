package org.intellij.sequencer;

import org.intellij.sequencer.diagram.Link;
import org.intellij.sequencer.diagram.MethodInfo;
import org.intellij.sequencer.diagram.ObjectInfo;
import org.intellij.sequencer.diagram.Parser;

import java.io.IOException;
import java.io.PushbackReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author rjf
 * @since 2020/11/18
 */
public class TextSequence {

    private static final Pattern IMPL_PATTERN = Pattern.compile("^(.*?\\.)impl\\.(\\w+)Impl$");

    public static String saveAsMermaid(String text, boolean mergeImpl, Set<String> ignoreClasses) throws IOException {
        Parser parser = new Parser();
        parser.parse(text);
        return saveAsMermaid(parser, mergeImpl, ignoreClasses);
    }

    public static String saveAsMermaid(PushbackReader reader, boolean mergeImpl, Set<String> ignoreClasses) throws IOException {
        Parser parser = new Parser();
        parser.parse(reader);
        return saveAsMermaid(parser, mergeImpl, ignoreClasses);
    }

    private static String saveAsMermaid(Parser parser, boolean mergeImpl, Set<String> ignoreClasses) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        Map<String, String> serviceNameMap = new HashMap<>();
        for (Link link : parser.getLinks()) {
            ObjectInfo from = link.getFrom();
            ObjectInfo to = link.getTo();
            boolean call = from.getSeq() <= to.getSeq();
            String fromFullName = from.getFullName();
            String toFullName = to.getFullName();
            if (ignoreClasses.contains(fromFullName) || ignoreClasses.contains(toFullName)) {
                continue;
            }
            if (mergeImpl && link.getCallerMethodInfo() != null && isImpl(link.getCallerMethodInfo(), link.getMethodInfo())) {
                // 合并服务实现
                if (fromFullName.endsWith("Impl")) {
                    serviceNameMap.put(fromFullName, to.getName());
                } else if (toFullName.endsWith("Impl")) {
                    serviceNameMap.put(toFullName, from.getName());
                }
                continue;
            }
            String fromName = serviceNameMap.getOrDefault(fromFullName, from.getName());
            String toName = serviceNameMap.getOrDefault(toFullName, to.getName());
            sb.append("    ");
            sb.append(fromName);
            sb.append(call ? "->>" : "-->>");
            sb.append(toName);
            sb.append(": ");
            if (call) {
                sb.append(link.getMethodInfo().getName());
                sb.append("(");
                sb.append(String.join(",", link.getMethodInfo().getArgNames()));
                sb.append(")");
            } else {
                String returnType = link.getMethodInfo().getReturnType();
                sb.append(returnType.replaceAll("[\\w.]*\\.([A-Z]\\w+)", "$1"));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static boolean isImpl(MethodInfo one, MethodInfo other){
        String oneClassName = one.getObjectInfo().getFullName();
        String otherClassName = other.getObjectInfo().getFullName();
        if (oneClassName.equals(otherClassName)) {
            return false;
        }

        oneClassName = IMPL_PATTERN.matcher(oneClassName).replaceAll("$1$2");
        otherClassName = IMPL_PATTERN.matcher(otherClassName).replaceAll("$1$2");
        // 属于接口或者实现
        return oneClassName.equals(otherClassName)
                // 方法名一致
                && one.getName().equals(one.getName())
                // 签名一致
                && String.join(",", one.getArgTypes()).equals(String.join(",", other.getArgTypes()));
    }

}
