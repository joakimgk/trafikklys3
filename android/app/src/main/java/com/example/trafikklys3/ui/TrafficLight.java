package com.example.trafikklys3.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.trafikklys3.R;
import com.example.trafikklys3.controller.TrafficLightListener;
import com.example.trafikklys3.model.Client;
import com.example.trafikklys3.model.LightStatus;
import com.example.trafikklys3.util.DimenUtils;

public class TrafficLight extends FrameLayout
{
    private static final float LAMP_ON = 1.0F;
    private static final float LAMP_OFF = 0.2F;

    View mContent;

    public int cellX = 0;
    public int cellY = 0;
    private int mOrientation = 0;

    private int lightId;
    private Client client;  // the unit that drives this light, if one is assigned

    private LinearLayout mLightContainer = null;
    private ImageView mStatusBadge;
    private LightStatus status = null;

    private TrafficLightListener listener;

    public TrafficLight(Context context)
    {
        super(context);
        init(context);
    }

    public TrafficLight(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }

    private void init(Context context)
    {
        LayoutInflater inflater = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        mContent = inflater.inflate(R.layout.view_traffic_light, this, true);

        // You could use the <merge> tag to avoid the extra viewgroup, but it nukes some attributes ( e.g. background )
        // from the xml, so easier to just keep it
        mLightContainer = mContent.findViewById(R.id.light_container);

        int badgeSize = (int) DimenUtils.calculatePixelFromDp(context, 20);
        int badgeMargin = (int) DimenUtils.calculatePixelFromDp(context, 2);
        mStatusBadge = new ImageView(context);
        LayoutParams lp = new LayoutParams(badgeSize, badgeSize, Gravity.TOP | Gravity.END);
        lp.setMargins(badgeMargin, badgeMargin, badgeMargin, badgeMargin);
        addView(mStatusBadge, lp);
        setStatus(LightStatus.UNASSIGNED);
        setLamps((byte) 0);  // nothing runs yet: every lamp dim
    }

    public void setListener(TrafficLightListener listener) {
        this.listener = listener;
    }

    public int getLightId() {
        return lightId;
    }

    public void setLightId(int id) {
        lightId = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setStatus(LightStatus newStatus) {
        if (newStatus == status) return;
        status = newStatus;
        switch (newStatus) {
            case ONLINE:
                mStatusBadge.setImageResource(R.drawable.ic_status_online);
                break;
            case OFFLINE:
                mStatusBadge.setImageResource(R.drawable.ic_status_offline);
                break;
            default:
                mStatusBadge.setImageResource(R.drawable.ic_status_unassigned);
        }
    }

    public LightStatus getStatus() {
        return status;
    }

    /** Outline around the light the user has picked. */
    public void setHighlighted(boolean highlighted) {
        if (!highlighted) {
            setForeground(null);
            return;
        }
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke((int) DimenUtils.calculatePixelFromDp(getContext(), 3), Color.WHITE);
        outline.setCornerRadius(DimenUtils.calculatePixelFromDp(getContext(), 6));
        setForeground(outline);
    }

    /** Sets position and orientation directly (no rotate-around-centre shift). */
    public void restore(int x, int y, int orientation)
    {
        mOrientation = orientation;
        mLightContainer.setOrientation(orientation % 180 == 0 ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        mLightContainer.setRotation(orientation == 180 || orientation == 270 ? 180 : 0);
        cellX = x;
        cellY = y;
        requestLayout();
    }

    /** Shows a program step: bit 0 = red, bit 1 = yellow, bit 2 = green (as in Programs). */
    public void setLamps(byte bits)
    {
        for (int i = 0; i < 3; i++) {
            mLightContainer.getChildAt(i).setAlpha(((bits >> i) & 1) != 0 ? LAMP_ON : LAMP_OFF);
        }
    }

    public int getCellOrientation()
    {
        return mOrientation;
    }

    public void rotate()
    {
        int newOrientation = (mOrientation + 90) % 360;
        setCellOrientation(newOrientation);
        if (newOrientation == 180) mLightContainer.setRotation(180);
        if (newOrientation == 0) mLightContainer.setRotation(0);
    }

    public void setCellOrientation(int newOrientation)
    {
        if (newOrientation == mOrientation)
            return;

        // Set LinearLayout we're using to house the lights, and shift our cell position
        // so that it appears to rotate around its center
        if (newOrientation % 180 == 0)
        {
            mOrientation = newOrientation;
            cellX += 1;
            cellY -= 1;
            mLightContainer.setOrientation(LinearLayout.VERTICAL);
        }
        else
        {
            mOrientation = newOrientation;
            cellX -= 1;
            cellY += 1;
            mLightContainer.setOrientation(LinearLayout.HORIZONTAL);

        }

        TrafficLightContainer container = (TrafficLightContainer) getParent();
        cellX = container.clampX(cellX);
        cellY = container.clampY(cellY);

        requestLayout();
    }

    boolean mDragging = false;
    float dragStartRawX = 0;
    float dragStartRawY = 0;

    float dragStartX = 0;
    float dragStartY = 0;

    private final float clickThreshold = 200;

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        int action = ev.getActionMasked();
        TrafficLightContainer parent = (TrafficLightContainer) getParent();

        switch(action)
        {
            case MotionEvent.ACTION_DOWN:
            {
                mDragging = true;

                // Raw is screen coordinates
                dragStartRawX = ev.getRawX();
                dragStartRawY = ev.getRawY();

                // And this is visual position of view
                dragStartX = getX();
                dragStartY = getY();

                break;
            }


            case MotionEvent.ACTION_UP:
            {
                // A short touch that hasn't gone far is a tap: pick this light
                float moved = (float) Math.hypot(ev.getRawX() - dragStartRawX, ev.getRawY() - dragStartRawY);
                if ( ev.getEventTime() - ev.getDownTime() < clickThreshold && moved < getMeasuredWidth() * 0.5f )
                {
                    this.setTranslationX(0);
                    this.setTranslationY(0);

                    if (listener != null) listener.onTrafficLightSelected(this);
                }
                else if (mDragging)
                {
                    // subtract current raw from start raw to figure out how we've moved,
                    // add to visual position we started at to get final position.
                    float finalX = dragStartX + ev.getRawX() - dragStartRawX;
                    float finalY = dragStartY + ev.getRawY() - dragStartRawY;

                    // Snap to grid
                    TrafficLightContainer.Coordinates indices = parent.indexFromPosition( finalX, finalY );
                    this.cellX = (int) indices.x;
                    this.cellY = (int) indices.y;

                    // Reset visual position.
                    // setX() SetY() includes the current position someow, this just resets it completely
                    // since we're moving to actual view to that location, instead of just moving the visualization around.
                    this.setTranslationX(0);
                    this.setTranslationY(0);

                    requestLayout();	// calls onLayout() in parent again
                    if (listener != null) {
                        listener.onTrafficLightSelected(this);
                        listener.onTrafficLightChanged(this);
                    }
                }

                mDragging = false;

                break;
            }

            case MotionEvent.ACTION_MOVE:
            {
                float finalX = dragStartX + ev.getRawX() - dragStartRawX;
                float finalY = dragStartY + ev.getRawY() - dragStartRawY;

                // Visually moves the view around, but doesn't actually move where it was placed by its parent
                setX(finalX);
                setY(finalY);

                // We're just moving the visual of the view around, without redrawing it, so no need to call invalidate() or requestLayout()

                break;
            }

            default:
            {
                break;
            }
        }

        return mDragging;
    }
}
