class UrlMappings {

    static mappings = {
        "/"(controller: "version")
        "/$controller/$action?/$id?"()
        "500"(view: '/error')
    }
}
