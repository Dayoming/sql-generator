# SQL Generator
> SQL TC 문서 작성 시간을 줄이고 쿼리 테스트를 용이하게 만들기 위해 만들어본 토이 프로젝트

<img width="1684" height="896" alt="sql_tc_generator_2" src="https://github.com/user-attachments/assets/bf65a88e-8b71-4195-bd7d-1f347676f3d0" />

MyBatis 매퍼 XML에 작성된 동적 SQL(`<select>`, `<update>`, `<insert>`, `<delete>`)을 그대로 붙여넣으면, 조건 분기를 직접 선택해가며 실제 DB 클라이언트에서 바로 실행 가능한 바인드 SQL로 변환해주는 웹 도구입니다. Spring Boot 백엔드와 순수 HTML/CSS/JS 프론트엔드로 구성되어 있습니다.
