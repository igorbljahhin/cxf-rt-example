package web

import com.example.client.ServicesClient
import com.example.dto.VersionDTO
import org.springframework.beans.factory.annotation.Autowired

class VersionController {
    @Autowired
    private ServicesClient servicesClient

    def index() {
        final VersionDTO version = servicesClient.getVersionResource(request).getCurrentVersion()

        render view: "index", model: [version: version]
    }
}
