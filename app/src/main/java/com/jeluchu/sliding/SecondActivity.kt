package com.jeluchu.sliding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jeluchu.sliding.Sliding.attach
import com.jeluchu.sliding.model.Config
import com.jeluchu.sliding.model.Position

class SecondActivity : AppCompatActivity() {

    private var mConfig: Config? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        mConfig = Config.Builder()
            .primaryColor(ContextCompat.getColor(this, R.color.purple_200))
            .secondaryColor(ContextCompat.getColor(this, R.color.teal_700))
            .position(Position.LEFT)
            .velocityThreshold(2400f)
            .touchSize(32.dpToPx.toFloat())
            .build()

        attach(this, mConfig!!)

    }
}