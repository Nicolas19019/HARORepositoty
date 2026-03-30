package com.Aplication.HARO.Service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractDocumentStorageServicePathTest {

    @Test
    void storeSignedContract_shouldOrganizeBySedeAndCategory() {
        ContractDocumentStorageService service = new ContractDocumentStorageService(
                "local",
                "target/test-uploads",
                "us-east-2",
                "",
                "",
                "",
                "contratos/",
                ""
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contrato.pdf",
                "application/pdf",
                "fake-pdf".getBytes()
        );

        ContractDocumentStorageService.StoredDocument stored = service.storeSignedContract(
                file,
                "Juan Perez",
                "12345678",
                "Contrato 1",
                "El Eden",
                "B1"
        );

        assertTrue(stored.objectKey().contains("el-eden/juan-perez/12345678/b1/"));
    }
}
