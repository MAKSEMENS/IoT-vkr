package ru.leti.vkr.aggregation.admin

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(private val replayService: ReplayService) {

    @PostMapping("/replay")
    fun replay(): ResponseEntity<ReplayService.ReplayResult> {
        val result = replayService.replay()
        return if (result.success) ResponseEntity.accepted().body(result)
        else ResponseEntity.status(409).body(result)
    }
}
