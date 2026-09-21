package com.jinw.worker.llm.utils;

import com.jinw.worker.llm.config.Options;
import com.jinw.worker.llm.config.Provider;
import com.jinw.worker.llm.config.ProviderModel;
import com.jinw.worker.llm.config.ProviderOptions;

import java.util.Objects;

public class ProviderCompareUtil {

    public static boolean isSameProvider(Provider a, Provider b) {

        // provider 级别
        if (!Objects.equals(a.getName(), b.getName())) return false;
        if (!Objects.equals(a.getNpm(), b.getNpm())) return false;

        if (!isSameOptions(a.getOptions(), b.getOptions())) return false;

        // model 级别
        if (a.getModels() == null || b.getModels() == null) return false;

        ProviderModel mb = b.getModels().values().stream().findFirst().orElse(null);
        ProviderModel ma = a.getModels().values().stream()
                .filter(value -> value.getName().equals(mb.getName())).findFirst().orElse(null);

        if (ma == null || mb == null) return false;

        if (!isSameModel(ma, mb)) return false;

        return true;
    }

    private static boolean isSameOptions(ProviderOptions a, ProviderOptions b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.getApiKey(), b.getApiKey())
                && Objects.equals(a.getBaseURL(), b.getBaseURL());
    }

    private static boolean isSameModel(ProviderModel a, ProviderModel b) {

        if (!Objects.equals(a.getName(), b.getName())) return false;

        // limit
        if (!Objects.equals(a.getLimit().getContext(), b.getLimit().getContext())) return false;
        if (!Objects.equals(a.getLimit().getOutput(), b.getLimit().getOutput())) return false;

        // thinking
        if (!isSameThinking(a.getOptions(), b.getOptions())) return false;

        return true;
    }

    private static boolean isSameThinking(Options a, Options b) {

        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        if (a.getThinking() == null && b.getThinking() == null) return true;
        if (a.getThinking() == null || b.getThinking() == null) return false;

        return Objects.equals(
                a.getThinking().getType(),
                b.getThinking().getType()
        ) && Objects.equals(
                a.getThinking().getBudgetTokens(),
                b.getThinking().getBudgetTokens()
        );
    }
}
