package doc.batch.mapper.biz;

import java.util.List;

/**
 * 업무 DB 매퍼 예제
 *  - doc.batch.mapper.biz 패키지에 두면 BizDataSourceConfig 의 @MapperScan 이 업무 DB 에 자동 바인딩
 *  - XML: src/main/resources/mapper/biz/SampleDocMapper.xml
 *  - 매퍼를 주입해서 쓸 때는 이름 지정이 필요 없다 (DB 연결은 설정에서 이미 끝남)
 */
public interface SampleDocMapper {

    List<SampleDocVo> selectWaitDocs();

    int updateDone(SampleDocVo vo);

    int countDone();
}
