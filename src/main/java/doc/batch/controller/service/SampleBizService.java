package doc.batch.controller.service;

import doc.batch.mapper.biz.SampleDocMapper;
import doc.batch.mapper.biz.SampleDocVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무 DB 를 쓰는 서비스 예제
 * <p>
 * ★ 핵심: 업무 DB 트랜잭션은 반드시 transactionManager = "bizTransactionManager" 를 적어야 한다.
 * 이름 없는 @Transactional 은 @Primary 인 메인 DB 트랜잭션으로 간다.
 */
@Service
public class SampleBizService {

    private final SampleDocMapper sampleDocMapper;

    public SampleBizService(SampleDocMapper sampleDocMapper) {
        this.sampleDocMapper = sampleDocMapper;
    }

    @Transactional(transactionManager = "bizTransactionManager")
    public int markDone(SampleDocVo vo) {
        return sampleDocMapper.updateDone(vo);
    }

    @Transactional(transactionManager = "bizTransactionManager", readOnly = true)
    public int countDone() {
        return sampleDocMapper.countDone();
    }
}
