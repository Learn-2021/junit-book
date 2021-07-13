package com.example.junitbook.ch06;

import com.example.junitbook.ch06.domain.BooleanAnswer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class StatCompilerTest {
    @Test
    void 질문별_답변_수_반환() {
        StatCompiler stats = new StatCompiler();
        // 질문 생성
        Map<Integer, String> questions = new HashMap<>();
        questions.put(1, "Tuition reimbursement?");
        questions.put(2, "Relocation package?");

        // 답변 생성
        List<BooleanAnswer> answers = new ArrayList<>();
        answers.add(new BooleanAnswer(1, true));
        answers.add(new BooleanAnswer(1, true));
        answers.add(new BooleanAnswer(1, true));
        answers.add(new BooleanAnswer(1, false));
        answers.add(new BooleanAnswer(2, true));
        answers.add(new BooleanAnswer(2, true));

        // 결과
        Map<String, Map<Boolean, AtomicInteger>> responses =
                stats.responseByQuestion(answers, questions);

        // 검증
        assertThat(responses.get("Tuition reimbursement?").get(Boolean.TRUE).get())
                .isEqualTo(3);
        assertThat(responses.get("Tuition reimbursement?").get(Boolean.FALSE).get())
                .isEqualTo(1);
        assertThat(responses.get("Relocation package?").get(Boolean.TRUE).get())
                .isEqualTo(2);
        assertThat(responses.get("Relocation package?").get(Boolean.FALSE).get())
                .isEqualTo(0);
    }
}