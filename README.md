# FIRST

테스트 코드도 우리가 유지-보수 해야 하는 코드다. 테스트에 다음과 같은 문제가 있다면 유지 비용이 많이 들 수 있다.

- 테스트를 사용하는 사람에게 어떤 정보도 주지 못한다.
- 산발적으로 실패한다
- 어떤 가치도 증명하지 못한다
- 실행하는 데 오래 걸린다
- 코드를 충분히 커버하지 못한다
- 구현과 강하게 결합되어 있어서 작은 변화에도 변경이 필요하다
- 수많은 설정 고리로 점프하는 난해한 테스트

FIRST 원칙을 따르면 단위 테스트를 작성할 때 흔히 하는 실수를 줄일 수 있다.

# Fast 빠른 테스트

빠른 테스트란 코드만 실행하며 소요 시간은 수 밀리초 수준을 말한다. 느린 테스트는 DB, 파일, 네트워크 호출처럼 외부 자원을 다루는 코드를 호출한다. 전형적인 자바 시스템은 단위 테스트 수천 개가 필요하다. 이는 평균 200밀리초로 계산하면 테스트 2500개를 실행하는 데 총 8분 이상 소요된다. 시스템이 커질 수록 소요되는 시간은 늘어난다. 단위 테스트를 전부 실행하는 시간이 너무 길어 하루에 서너 번도 실행하는 것도 부담스러운 상황이라면 어딘가 잘못된 방향으로 가고 있을 확률이 높다.

아무튼.

테스트가 빨리 실행되도록 유지하는 것은 중요하다. 테스트가 느리다면 느린 테스트에 대한 의존성을 제거해야 한다. `responsesByQuestion()` 메서드에 대한 테스트를 작성해보면서 테스트를 빠르게 만드는 방법을 간략하게 살펴본다. 이 메서드는 질문을 받고 질문에 대해 true/false 답변 히스토그램을 반환한다.

```java
public class StatCompiler {
    private QuestionController controller = new QuestionController();

    public Map<String, Map<Boolean, AtomicInteger>> responseByQuestion(
            List<BooleanAnswer> answers) {
        Map<Integer, Map<Boolean, AtomicInteger>> responses = new HashMap<>();
        answers.stream().forEach(answer -> incrementHistogram(responses, answer));
        return convertHistogramIdsToText(responses);
    }

    private Map<String, Map<Boolean, AtomicInteger>> convertHistogramIdsToText(
            Map<Integer, Map<Boolean, AtomicInteger>> responses) {
        Map<String, Map<Boolean, AtomicInteger>> textResponses = new HashMap<>();
        responses.keySet()
                .stream()
                .forEach(id -> textResponses.put(controller.find(id).getText(), responses.get(id)));
        return textResponses;
    }

    private void incrementHistogram(
            Map<Integer, Map<Boolean, AtomicInteger>> responses,
            BooleanAnswer answer
    ) {
        Map<Boolean, AtomicInteger> histogram =
                getHistogram(responses, answer.getQuestionId());
        histogram.get(Boolean.valueOf(answer.getValue())).getAndIncrement();
    }

    private Map<Boolean, AtomicInteger> getHistogram(
            Map<Integer, Map<Boolean, AtomicInteger>> responses, int id
    ) {
        Map<Boolean, AtomicInteger> histogram = null;
        if (responses.containsKey(id)) {
            histogram = responses.get(id);
        } else {
            histogram = createNewHistogram();
            responses.put(id, histogram);
        }
        return histogram;
    }

    private Map<Boolean, AtomicInteger> createNewHistogram() {
        Map<Boolean, AtomicInteger> histogram;
        histogram = new HashMap<>();
        histogram.put(Boolean.FALSE, new AtomicInteger(0));
        histogram.put(Boolean.TRUE, new AtomicInteger(0));
        return histogram;
    }
}
```

히스토그램은 `Boolean` 값의 매핑에 대한 `Map` 이다. `responses` 해시맵은 질문ID와 그에 대한 히스토그램을 매핑한다. `incrementHistogram()` 메서드는 주어진 질문에 대해 히스토그램을 갱신한다. 마지막으로 `convertHistogramIdsToText()` 메서드는 `responses` 맵을 질문 '텍스트 : 히스토그램'으로 매핑한다.

불행하게도 `convertHistogramIdsToText()` 메서드는 테스트 도전 과제에 해당한다. `QuestionController` 객체의 `find()` 를 호출하면 느린 영속적 저장소와 상호 작용한다. 그렇게 되면 테스트가 느릴 뿐 아니라 테스트를 위해 데이터베이스까지 실행해야 한다. 데이터베이스와 데이터 테스트에서 기대하는 데이터 값의 거리 때문에 테스트는 따르기 어렵고 불안하다.

질문을 얻기 위해 컨트롤러에 질의하기보다 먼저 질문을 가져오고, 그 텍스트를 `responsesByQuestion()` 메서드의 인수로 넘긴다. 먼저 대답에 대한 질문 ID와 질문 내용의 맵을 생성하는 `questionText()` 메서드를 생성한다.

```java
public Map<Integer, String> questionText(List<BooleanAnswer> answers) {
        Map<Integer, String> questions = new HashMap<>();
        answers.stream().forEach(answer -> {
            if (!questions.containsKey(answer.getQuestionId())) {
                questions.put(answer.getQuestionId(),
                        controller.find(answer.getQuestionId()).getText());
            }
        });
        return questions;
    }
```

`responsesByQuestion()` 에 질문 ID와 내용을 매핑하는 questions변수를 추가한다.

```java
public Map<String, Map<Boolean, AtomicInteger>> responseByQuestion(
            List<BooleanAnswer> answers,
            Map<Integer, String> questions) {
        Map<Integer, Map<Boolean, AtomicInteger>> responses = new HashMap<>();
        answers.stream().forEach(answer -> incrementHistogram(responses, answer));
        return convertHistogramIdsToText(responses, questions);
    }
```

`responsesByQuestion()` 은 `convertHistogramIdsToText()` 에 `questions` 맵을 넘긴다.

```java
private Map<String, Map<Boolean, AtomicInteger>> convertHistogramIdsToText(
            Map<Integer, Map<Boolean, AtomicInteger>> responses,
            Map<Integer, String> questions) {
        Map<String, Map<Boolean, AtomicInteger>> textResponses = new HashMap<>();
        responses.keySet()
                .stream()
                .forEach(id -> textResponses.put(questions.get(id), responses.get(id)));
        return textResponses;
    }
```

`questionsText()` 에 있는 코드는 여전히 느린 영속 영역에 의존한다. 하지만 우리가 테스트 하려는 코드는 작은 부분이다. `convertHistogramIdsText()` 메서드는 메모리 상의 해시 맵만 사용하며 느린 영속 영역을 조회하지 않는다. 이제 `responsesByQuestion()` 메서드를 손쉽게 테스트할 수 있다.

```java
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
```



이 테스트는 `responsesByQuestion()` , `convertHistogramIdsToText()` , `incrementHistogram()` 메서드에 있는 많은 로직을 포함한다. 우리는 세 메서드에 있는 로직을 조합하여 흥미로운 테스트를 작성할 수 있다. 그렇게 하면 손쉽게 다수의 테스트를 가지게 된다. 더 많은 로직을 커버하는 소수의 빠른 테스트는 데이터베이스 호출에 의존하는 단일 테스트보다 수월하고 빠르게 실행된다.

테스트 코드는 빠르게 동작하며, 느린 것에 의존하는 코드를 최소화한다면, 작성하기 쉬워진다. 이러한 의존성을 최소화하는 것 역시 좋은 설계의 목표다. 코드를 객체지향 설계 개념과 맞출 수록 단위 테스트를 작성하기 쉬워진다.

참고: 자바와 JUnit을 활용한 실용주의 단위 테스트

[자바와 JUnit을 활용한 실용주의 단위 테스트 - YES24](http://www.yes24.com/Product/Goods/75189146)