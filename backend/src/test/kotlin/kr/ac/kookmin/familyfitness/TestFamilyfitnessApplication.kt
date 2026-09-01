package kr.ac.kookmin.familyfitness

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<FamilyfitnessApplication>().with(TestcontainersConfiguration::class).run(*args)
}
