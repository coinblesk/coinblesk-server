package com.coinblesk.server.utilTest;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@RunWith(SpringRunner.class)
@Transactional
@TestPropertySource(properties = {
	"spring.datasource.url: jdbc:h2:mem:testdb",
	"bitcoin.net:unittest"
})
public abstract class CoinbleskTest {
}
