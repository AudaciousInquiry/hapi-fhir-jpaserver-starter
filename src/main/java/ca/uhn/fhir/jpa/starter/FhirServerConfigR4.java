package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.jpa.config.BaseJavaConfigR4;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import com.ainq.saner.SanerServerCsvTransformOperation;
import com.ainq.saner.SanerServerCustomizer;
import com.ainq.saner.SanerServerHelloWorldOperation;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@Configuration
public class FhirServerConfigR4 extends BaseJavaConfigR4 {

  @Autowired
  private DataSource myDataSource;

  /**
   * We override the paging provider definition so that we can customize the default/max page sizes
   * for search results. You can set these however you want, although very large page sizes will
   * require a lot of RAM.
   */
  @Override
  public DatabaseBackedPagingProvider databaseBackedPagingProvider() {
    DatabaseBackedPagingProvider pagingProvider = super.databaseBackedPagingProvider();
    pagingProvider.setDefaultPageSize(HapiProperties.getDefaultPageSize());
    pagingProvider.setMaximumPageSize(HapiProperties.getMaximumPageSize());
    return pagingProvider;
  }

  @Override
  @Bean()
  public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
    LocalContainerEntityManagerFactoryBean retVal = super.entityManagerFactory();
    retVal.setPersistenceUnitName("HAPI_PU");

    try {
      retVal.setDataSource(myDataSource);
    } catch (Exception e) {
      throw new ConfigurationException("Could not set the data source due to a configuration issue",
        e);
    }

    retVal.setJpaProperties(HapiProperties.getJpaProperties());
    return retVal;
  }

  @Bean()
  public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    JpaTransactionManager retVal = new JpaTransactionManager();
    retVal.setEntityManagerFactory(entityManagerFactory);
    return retVal;
  }

  @Bean(name = "sanerHelloWorldOperation")
  public SanerServerHelloWorldOperation instanceLoaderProvider() {
    return new SanerServerHelloWorldOperation();
  }

  @Bean(name = "sanerCsvTransformOperation")
  public SanerServerCsvTransformOperation csvTransformProvider(){
    return new SanerServerCsvTransformOperation();
  }

  @Bean(name = "sanerServerCustomizer")
  public SanerServerCustomizer sanerServerCustomizer() {
    return new SanerServerCustomizer();
  }

  @PostConstruct
  private void init() {
    System.out.println("DEBUG ----> IN INSTANCE LOADER POST CONSTRUCT!");
  }
}
