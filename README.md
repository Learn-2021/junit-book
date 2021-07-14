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

# [F]IRST 빠른 테스트
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

# F[Isolated]RST 고립된 테스트

좋은 단위 테스트는 검증하려는 작은 양의 코드에 집중한다. 이것은 우리가 '단위'라고 말하는 정의와 부합한다. 직접적 혹은 간접적으로 테스트 코드와 상호 작용하는 코드가 많을수록 문제가 발생할 소지가 늘어난다.

테스트 대상 코드는 데이터베이스를 읽는 다른 코드와 상호 작용할 수도 있다. 데이터 의존성은  많은 문제를 만든다. 궁극적으로 데이터베이스에 의존해야 하는 테스트는 데이터베이스가 올바른 데이터를 가지고 있는지 확인해야 한다. 데이터 소스를 공유한다면 테스트를 깨뜨리는 외부 변화도 고려해야 한다. 단순히 외부 저장소와 상호 작용하게 되면 테스트 가용성availability 혹은 접근성 이슈로 실패할 가능성이 증가한다.

또 좋은 단위 테스트는 다른 단위 테스트에 의존하지 않는다. 아마도 여러 테스트가 값비싸게 생성된 데이터를 재사용하는 방식으로 순서를 조작하여 전체 테스트의 실행 속도를 높이려고 할 수도 있다.  하지만 이럴 경우 의존성의 악순환만 발생한다. 일이 잘못되면 테스트가 실패할 때, 실패하게 된 이유를 거슬러 올라가서 무엇이 원인인지 알아내느라 긴 시간을 들여야 할 수도 있다.

따라서 테스트 코드는 어떤 순서나 시간에 관계없이 실행할 수 있어야 한다. 각 테스트가 작은 양의 동작에만 집중하면 테스트 코드를 집중적이고 독립적으로 유지하기 쉬워진다. 테스트에 두 번째 단언을 추가할 때 다음과 같이 질문해야 한다.

"이 단언Assert이 단일 동작을 검증하는가? 아니면 내가 새로운 테스트 이름으로 기술할 수 있는 어떤 동작을 대표하는가?"~~(이 부분은 저도 무슨 말인 지 잘 이해 못했습니다.)~~

객체 지향 클래스 설계의 단일 책임 원칙에 따르면, 클래스는 작고 단일한 목적을 가져야 한다. 좀 더 궤적으로 SRP는 클래스를 변경해야 할 이유가 하나만 있어야 한다고 주장한다. 또한 SRP는 테스트 메서드에서도 훌륭한 지침을 제공한다. 테스트 메서드가 하나 이상의 이유로 깨진다면 테스트를 분할하는 것도 고려해야 한다. 집중적인 단위 테스트가 깨지면 대개 그 원인은 분명하다.

# FI[Repeatable]ST 반복 가능한 테스트

테스트는 뜬금없이 나오면 안 된다. 테스트는 테스트 설계자 통제 아래 있어야 한다. 또 테스트 조건을 고안할 힘도 있고, 테스트 코드 자체로 그 내용을 설명할 수 있어야 한다. 테스트 설계에서 우리의 역할 중 하나는 테스트 결과가 매번 어떻게 나와야 하는지에 대해 설명하는 Assert를 제공하는 것이다.

**반복 가능한 테스트는 실행할 때마다 결과가 같아야 한다.** 따라서 반복 가능한 테스트를 만들려면 직접 통제할 수 없는 외부 환경에 있는 항목과 격리시켜야 한다.

하지만 시스템은 불가피하게 통제할 수 없는 요소와 상호작용해야 한다. 예를 들어, 현재 시간을 다루어야 한다면 테스트 코드는 반복 가능한 테스트를 힘들게 하는 요소가 된다. 이때 테스트 대상 코드의 나머지를 격리하고, 시간 변환에 독립성을 유지하는 방법으로 Mock 객체를 사용할 수 있다.

`iloveyouboss` 라는 애플리케이션이 있고, 이 애플리케이션은 새로운 질문이 `Profile` 에 추가되었을 때 생성 타임스탬프가 저장되는 것을 검증하고자 한다. TIMESTAMP는 움직이는 표적이므로 특정 TIMESTAMP를 assert하는 것은 어려운 일이다.

테스트 프로파일에 질문을 추가한 후에는 즉시 시스템 시간을 요청할 수 있다. 대부분의 밀리초는 걱정하지 않아도 되므로 지속된 시간과 테스트 코드의 시간을 비교한다. 대부분 잘 동작하지만, 지속된 시간이 17:34:05.999라면 테스트는 실패할 수도 있다.

이처럼 산발적으로 실패하는 테스트는 골칫거리다. 때때로  테스트가 동시에 실행되는 코드를 주도하면 시스템 결함이 드러나기도 한다. 하지만 더 자주 간헐적으로 실패하는 테스트는 거짓말을 하는 테스트가 될 수도 있다.

이럴 때 코드가 진짜 시간을 가진 것처럼 속일 수 있다. 자바8에서는 `java.time.Clock` 객체를 사용하여 고정된 시간을 반환할 수 있다. 테스트에서 현재 시간을 얻으려는 코드에 가짜 `Clock` 객체를 넘길 수 있다.

```java
@Test
void questionAnswersDateAdded() {
	Instant now = new Date().toInstant();
  controller.setClock(Clock.fixed(now, ZoneId.of("America/Denver")));
  int id = controller.addBooleanQuestion("text");
  Question question = controller.find(id);

  assertThat(question.getCreateTimestamp()).isEqualTo(now);
}
```



이 예제에서 첫 번째 행은 `Instance` 객체를 생성하여 변수 `now` 에 저장한다.

두 번째 행은 `now` 인스턴스를 넘겨 `Clock` 객체를 생성한 다음, setter를 사용하여 컨트롤러에 주입한다. 테스트의 assert는 질문의 생성 타임스탬프가 `now` 와 동일한지 검사한다.

```java
public class QuestionController {
	private Clock clock = Clock.systemUTC();
	//..
	public int addBooleanQuestion(String text) {
		return persist(new BooleanQuestion(text));
	}
	void setClock(Clock clock) {
		this.clock = clock;
	}
	//..
	private int persist(Persistable object) {
		object.setCreateTimestamp(clock.instant());
		executeInTransaction((em) => em.persist(object));
		return object.getId();
	}
}
```

`persist()` 는 주입된 `clock` 인스턴스에서 `Instant` 객체를 얻어 `Persistable` 객체에 `setCreateTimestamp()` 메서드 인자로 넘긴다. 클라이언트가 `setClock()` 을 호출하여 `Clock` 인스턴스를 주입하지 않으면 기본적으로 필드 수준에서 초기화된 `systemUTC` 를 사용한다.

이렇게 해서 `QuestionController` 는 `Clock` 객체의 출처는 신경쓰지 않고 오직 현재의 `Instant` 객체로 대답한다. 테스트에서 사용된 시계는 실제 것을 대표하는 테스트 더블 역할을 한다.

반복 가능성을 충족하지 않는 테스트는 버그가 아닌 데도 버그인 것처럼 개발자를 오해하게 만들 수 있다. 따라서 테스트는 항상 동일한 결과를 반복해야 한다.


참고: 자바와 JUnit을 활용한 실용주의 단위 테스트

[자바와 JUnit을 활용한 실용주의 단위 테스트 - YES24](http://www.yes24.com/Product/Goods/75189146)