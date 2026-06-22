// =============================================
// BatchScheduleMapper.java
// =============================================
package doc.batch.controller.service.mapper;

import doc.batch.controller.service.BatchRunHistVo;
import doc.batch.controller.service.BatchScheduleVo;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface BatchScheduleMapper {

    /** 활성 스케줄 전체 조회 */
    List<BatchScheduleVo> selectActiveSchedules();

    /** 단건 조회 */
    BatchScheduleVo selectSchedule(String jobName);

    /** 크론 표현식 수정 */
    int updateCronExpr(BatchScheduleVo vo);

    /** 사용여부 수정 */
    int updateUseYn(BatchScheduleVo vo);

    /** 실행 이력 등록 */
    int insertRunHist(BatchRunHistVo vo);

    /** 실행 이력 종료 업데이트 */
    int updateRunHist(BatchRunHistVo vo);

    /** 실행 이력 목록 */
    List<BatchRunHistVo> selectRunHistList(String jobName);
}
